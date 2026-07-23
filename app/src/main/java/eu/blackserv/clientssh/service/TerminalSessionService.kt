package eu.blackserv.clientssh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import eu.blackserv.clientssh.MainActivity
import eu.blackserv.clientssh.R
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.storage.LocalAppStore
import eu.blackserv.clientssh.terminal.PendingSessionRegistry
import eu.blackserv.clientssh.terminal.TerminalConnectionState
import eu.blackserv.clientssh.terminal.TerminalSessionBus
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TerminalSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appStore by lazy { LocalAppStore(applicationContext) }
    private val sessionPrefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var sshSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var shellOutput: OutputStream? = null
    private var activeProfile: HostProfile? = null

    @Volatile private var maintainSession = false
    @Volatile private var sessionKeeperEnabled = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnectAndStop(startId, "Rozłączono ręcznie")
            return START_NOT_STICKY
        }

        sessionKeeperEnabled = appStore.loadTerminalSettings().backgroundSessionEnabled
        val requestedProfileId = intent?.getStringExtra(EXTRA_PROFILE_ID)?.takeIf(String::isNotBlank)
        val restoringSession = requestedProfileId == null

        if (restoringSession && !sessionKeeperEnabled) {
            clearRememberedSession()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val profileId = requestedProfileId
            ?: sessionPrefs.getString(KEY_ACTIVE_PROFILE_ID, null)?.takeIf(String::isNotBlank)
        if (profileId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            createNotification(
                profileName = activeProfile?.name.orEmpty().ifBlank { "Terminal" },
                status = if (restoringSession) "Przywracanie sesji…" else "Łączenie…",
                profileId = profileId,
            ),
        )

        val profile = PendingSessionRegistry.get(profileId)
            ?: appStore.loadProfiles().firstOrNull { it.id == profileId }
        if (profile == null) {
            clearRememberedSession()
            TerminalSessionBus.markError("Nie udało się odtworzyć profilu aktywnej sesji.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val currentState = TerminalSessionBus.snapshot.value
        val alreadyRunning = activeProfile?.id == profile.id &&
            connectionJob?.isActive == true &&
            currentState.state in setOf(TerminalConnectionState.CONNECTING, TerminalConnectionState.CONNECTED)
        if (alreadyRunning) return if (sessionKeeperEnabled) START_STICKY else START_NOT_STICKY

        activeProfile?.id?.takeIf { it != profile.id }?.let(PendingSessionRegistry::remove)
        activeProfile = profile
        PendingSessionRegistry.put(profile)
        maintainSession = true

        if (restoringSession) {
            TerminalSessionBus.markReconnecting(
                profile = profile,
                status = "Przywracanie sesji…",
                notice = "Android ponownie uruchomił Session Keeper. Przywracam połączenie.",
            )
        } else {
            if (sessionKeeperEnabled) rememberActiveSession(profile.id, connected = false) else clearRememberedSession()
            if (
                currentState.profileId != profile.id ||
                currentState.state !in setOf(TerminalConnectionState.CONNECTING, TerminalConnectionState.CONNECTED)
            ) {
                TerminalSessionBus.begin(profile)
            }
        }

        updateNotification(profile.name, if (restoringSession) "Przywracanie połączenia…" else "Łączenie…", profile.id)
        startConnection(profile, reconnecting = restoringSession)
        return if (sessionKeeperEnabled) START_STICKY else START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!sessionKeeperEnabled) disconnectAndStop(0, "Sesja zakończona po zamknięciu aplikacji")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        reconnectJob?.cancel()
        closeTransport()
        serviceScope.cancel()

        val profile = activeProfile
        if (maintainSession && sessionKeeperEnabled && profile != null) {
            TerminalSessionBus.markReconnecting(
                profile = profile,
                status = "Session Keeper czeka na restart…",
                notice = "Usługa została zatrzymana przez Androida. Sesja zostanie przywrócona.",
            )
        } else if (TerminalSessionBus.snapshot.value.state == TerminalConnectionState.CONNECTED) {
            TerminalSessionBus.markDisconnected("Sesja zamknięta")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startConnection(profile: HostProfile, reconnecting: Boolean) {
        reconnectJob?.cancel()
        connectionJob?.cancel()
        closeTransport()
        connectionJob = serviceScope.launch {
            when (profile.protocol) {
                ConnectionProtocol.SSH -> runSsh(profile, reconnecting)
                ConnectionProtocol.TELNET -> failPermanently(
                    profile,
                    "Transport Telnet zostanie podłączony po ustabilizowaniu sesji SSH.",
                )
            }
        }
    }

    private suspend fun runSsh(profile: HostProfile, reconnecting: Boolean) {
        val jsch = JSch()
        val host = profile.host.trim()
        val username = profile.username.trim()
        var connectedOnce = false
        var shellEndedNormally = false

        if (reconnecting && TerminalSessionBus.snapshot.value.state != TerminalConnectionState.CONNECTING) {
            TerminalSessionBus.markReconnecting(profile)
        }

        try {
            if (host.isBlank()) error("Host jest pusty. Edytuj profil i wpisz domenę albo IP.")
            if (username.isBlank()) error("Użytkownik SSH jest pusty. Edytuj profil i wpisz login.")

            val knownHosts = File(filesDir, "ssh/known_hosts")
            knownHosts.parentFile?.mkdirs()
            if (!knownHosts.exists()) knownHosts.createNewFile()
            jsch.setKnownHosts(knownHosts.absolutePath)

            if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY) {
                val passphrase = profile.privateKeyPassphrase.takeIf(String::isNotEmpty)
                    ?.toByteArray(StandardCharsets.UTF_8)
                jsch.addIdentity(
                    profile.id,
                    profile.privateKey.toByteArray(StandardCharsets.UTF_8),
                    null,
                    passphrase,
                )
            }

            val session = jsch.getSession(username, host, profile.port).apply {
                if (profile.authenticationMethod == AuthenticationMethod.PASSWORD) setPassword(profile.password)
                setConfig("StrictHostKeyChecking", "accept-new")
                setConfig(
                    "PreferredAuthentications",
                    when (profile.authenticationMethod) {
                        AuthenticationMethod.PASSWORD -> "password,keyboard-interactive"
                        AuthenticationMethod.PRIVATE_KEY -> "publickey"
                        AuthenticationMethod.INTERACTIVE -> "keyboard-interactive,password"
                    },
                )
                setServerAliveInterval(KEEPALIVE_INTERVAL_MS)
                setServerAliveCountMax(KEEPALIVE_MAX_MISSES)
            }
            sshSession = session
            session.connect(CONNECT_TIMEOUT_MS)

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType("xterm-256color")
            val input = channel.inputStream
            val output = channel.outputStream
            shellChannel = channel
            shellOutput = output
            channel.connect(CHANNEL_TIMEOUT_MS)

            connectedOnce = true
            if (sessionKeeperEnabled) rememberActiveSession(profile.id, connected = true)
            TerminalSessionBus.attachWriter { bytes ->
                serviceScope.launch {
                    runCatching {
                        synchronized(output) {
                            output.write(bytes)
                            output.flush()
                        }
                    }.onFailure { closeTransport() }
                }
            }

            val keeperLabel = if (sessionKeeperEnabled) " • Session Keeper" else ""
            TerminalSessionBus.markConnected("SSH • $host:${profile.port}$keeperLabel")
            updateNotification(
                profile.name,
                if (sessionKeeperEnabled) "SSH działa w tle — wybierz WRÓĆ albo ROZŁĄCZ" else "SSH aktywne do zamknięcia aplikacji",
                profile.id,
            )

            val buffer = ByteArray(8 * 1024)
            while (serviceScope.isActive && maintainSession && channel.isConnected) {
                val read = input.read(buffer)
                if (read < 0) {
                    shellEndedNormally = true
                    break
                }
                if (read > 0) TerminalSessionBus.append(buffer, read)
            }

            when {
                shellEndedNormally -> finishShellSession(profile, "Sesja zakończona przez powłokę")
                maintainSession && sessionKeeperEnabled -> scheduleReconnect(profile, "Powłoka SSH została przerwana.")
                else -> finishShellSession(profile, "Sesja zakończona")
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (error: Throwable) {
            val readable = error.readableMessage(host)
            val sessionWasPreviouslyConnected = connectedOnce || sessionPrefs.getBoolean(KEY_SESSION_WAS_CONNECTED, false)
            if (maintainSession && sessionKeeperEnabled && sessionWasPreviouslyConnected && error.isRetryable()) {
                scheduleReconnect(profile, readable)
            } else {
                failPermanently(profile, readable)
            }
        } finally {
            TerminalSessionBus.detachWriter()
            closeTransport()
            jsch.removeAllIdentity()
            if (!maintainSession) {
                PendingSessionRegistry.remove(profile.id)
                activeProfile = null
            }
        }
    }

    private fun finishShellSession(profile: HostProfile, message: String) {
        maintainSession = false
        reconnectJob?.cancel()
        clearRememberedSession()
        TerminalSessionBus.markDisconnected(message)
        PendingSessionRegistry.remove(profile.id)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleReconnect(profile: HostProfile, reason: String) {
        if (!maintainSession || !sessionKeeperEnabled) return
        TerminalSessionBus.markReconnecting(
            profile = profile,
            status = "Ponowne łączenie za ${RECONNECT_DELAY_MS / 1_000} s…",
            notice = "$reason Ponawiam połączenie za ${RECONNECT_DELAY_MS / 1_000} s.",
        )
        updateNotification(
            profile.name,
            "Połączenie przerwane — ponawiam za ${RECONNECT_DELAY_MS / 1_000} s",
            profile.id,
        )
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(RECONNECT_DELAY_MS)
            if (maintainSession && sessionKeeperEnabled) startConnection(profile, reconnecting = true)
        }
    }

    private fun failPermanently(profile: HostProfile, message: String) {
        maintainSession = false
        reconnectJob?.cancel()
        clearRememberedSession()
        TerminalSessionBus.markError(message)
        updateNotification(profile.name, "Sesja zatrzymana: $message", profile.id)
        stopSelf()
    }

    private fun disconnectAndStop(startId: Int, message: String) {
        maintainSession = false
        reconnectJob?.cancel()
        connectionJob?.cancel()
        closeTransport()
        clearRememberedSession()
        TerminalSessionBus.markDisconnected(message)
        activeProfile?.id?.let(PendingSessionRegistry::remove)
        activeProfile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (startId > 0) stopSelf(startId) else stopSelf()
    }

    private fun rememberActiveSession(profileId: String, connected: Boolean) {
        sessionPrefs.edit()
            .putString(KEY_ACTIVE_PROFILE_ID, profileId)
            .putBoolean(KEY_SESSION_WAS_CONNECTED, connected)
            .apply()
    }

    private fun clearRememberedSession() {
        sessionPrefs.edit()
            .remove(KEY_ACTIVE_PROFILE_ID)
            .remove(KEY_SESSION_WAS_CONNECTED)
            .apply()
    }

    private fun closeTransport() {
        shellOutput = null
        runCatching { shellChannel?.disconnect() }
        runCatching { sshSession?.disconnect() }
        shellChannel = null
        sshSession = null
    }

    private fun createNotification(profileName: String, status: String, profileId: String?): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_ACTIVE_TERMINAL)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (profileId != null) openIntent.putExtra(EXTRA_PROFILE_ID, profileId)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val disconnectIntent = Intent(this, TerminalSessionService::class.java).setAction(ACTION_DISCONNECT)
        if (profileId != null) disconnectIntent.putExtra(EXTRA_PROFILE_ID, profileId)
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(profileName.ifBlank { "Client SSH" })
            .setContentText(status)
            .setSubText("Sesja SSH aktywna")
            .setContentIntent(openPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .addAction(android.R.drawable.ic_menu_view, "WRÓĆ", openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ROZŁĄCZ", disconnectPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(profileName: String, status: String, profileId: String?) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            createNotification(profileName, status, profileId),
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aktywna sesja SSH",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Stałe połączenie SSH z przyciskami WRÓĆ i ROZŁĄCZ"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun Throwable.isRetryable(): Boolean {
        val raw = message.orEmpty().lowercase()
        return listOf(
            "auth fail",
            "authentication",
            "invalid privatekey",
            "invalid private key",
            "reject hostkey",
            "host key has changed",
            "host jest pusty",
            "użytkownik ssh jest pusty",
        ).none(raw::contains)
    }

    private fun Throwable.readableMessage(host: String): String {
        val raw = message?.trim().orEmpty()
        return when {
            raw.contains("Auth fail", ignoreCase = true) -> "Nieprawidłowy login, hasło lub klucz SSH."
            raw.contains("invalid privatekey", ignoreCase = true) || raw.contains("invalid private key", ignoreCase = true) ->
                "Nieobsługiwany albo uszkodzony klucz prywatny."
            raw.contains("reject HostKey", ignoreCase = true) -> "Klucz hosta SSH zmienił się. Połączenie zostało zablokowane."
            raw.contains("UnknownHostException", ignoreCase = true) || raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Nie można znaleźć hosta: $host. Sprawdź internet, DNS albo literówkę w profilu."
            raw.contains("timeout", ignoreCase = true) -> "Przekroczono czas oczekiwania na połączenie."
            raw.isNotEmpty() -> raw
            else -> javaClass.simpleName
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val ACTION_DISCONNECT = "eu.blackserv.clientssh.action.DISCONNECT_SESSION"
        private const val CHANNEL_ID = "terminal_session_actions_v2"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "terminal_session_keeper"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_SESSION_WAS_CONNECTED = "session_was_connected"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val CHANNEL_TIMEOUT_MS = 10_000
        private const val KEEPALIVE_INTERVAL_MS = 15_000
        private const val KEEPALIVE_MAX_MISSES = 3
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
