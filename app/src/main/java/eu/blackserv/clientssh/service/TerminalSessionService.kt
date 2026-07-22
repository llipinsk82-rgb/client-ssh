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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TerminalSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var connectionJob: Job? = null
    private var sshSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var shellOutput: OutputStream? = null
    private var activeProfile: HostProfile? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnectFromNotification()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        val profile = PendingSessionRegistry.get(profileId)
        activeProfile = profile

        startForeground(
            NOTIFICATION_ID,
            createNotification(profile?.name.orEmpty().ifBlank { "Terminal" }, "Łączenie…", profile?.id),
        )

        if (profile == null) {
            TerminalSessionBus.markError("Nie udało się odtworzyć danych sesji.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        connectionJob?.cancel()
        closeTransport()
        connectionJob = serviceScope.launch {
            when (profile.protocol) {
                ConnectionProtocol.SSH -> runSsh(profile)
                ConnectionProtocol.TELNET -> TerminalSessionBus.markError(
                    "Transport Telnet zostanie podłączony po ustabilizowaniu sesji SSH.",
                )
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        closeTransport()
        serviceScope.cancel()
        if (TerminalSessionBus.snapshot.value.state == TerminalConnectionState.CONNECTED) {
            TerminalSessionBus.markDisconnected("Sesja zamknięta")
        }
        activeProfile?.id?.let { PendingSessionRegistry.remove(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runSsh(profile: HostProfile) {
        val jsch = JSch()
        val host = profile.host.trim()
        val username = profile.username.trim()
        try {
            if (host.isBlank()) error("Host jest pusty. Edytuj profil i wpisz domenę albo IP.")
            if (username.isBlank()) error("Użytkownik SSH jest pusty. Edytuj profil i wpisz login.")

            val knownHosts = File(filesDir, "ssh/known_hosts")
            knownHosts.parentFile?.mkdirs()
            if (!knownHosts.exists()) knownHosts.createNewFile()
            jsch.setKnownHosts(knownHosts.absolutePath)

            if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY) {
                val passphrase = profile.privateKeyPassphrase
                    .takeIf(String::isNotEmpty)
                    ?.toByteArray(StandardCharsets.UTF_8)
                jsch.addIdentity(
                    profile.id,
                    profile.privateKey.toByteArray(StandardCharsets.UTF_8),
                    null,
                    passphrase,
                )
            }

            val session = jsch.getSession(username, host, profile.port).apply {
                if (profile.authenticationMethod == AuthenticationMethod.PASSWORD) {
                    setPassword(profile.password)
                }
                setConfig("StrictHostKeyChecking", "accept-new")
                setConfig(
                    "PreferredAuthentications",
                    when (profile.authenticationMethod) {
                        AuthenticationMethod.PASSWORD -> "password,keyboard-interactive"
                        AuthenticationMethod.PRIVATE_KEY -> "publickey"
                        AuthenticationMethod.INTERACTIVE -> "keyboard-interactive,password"
                    },
                )
                setServerAliveInterval(15_000)
                setServerAliveCountMax(3)
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

            TerminalSessionBus.attachWriter { bytes ->
                serviceScope.launch {
                    runCatching {
                        synchronized(output) {
                            output.write(bytes)
                            output.flush()
                        }
                    }.onFailure { error ->
                        TerminalSessionBus.markError(error.readableMessage(host))
                    }
                }
            }
            TerminalSessionBus.markConnected("SSH • $host:${profile.port}")
            updateNotification(profile.name, "SSH działa w tle — dotknij, żeby wrócić", profile.id)

            val buffer = ByteArray(8 * 1024)
            while (serviceScope.isActive && channel.isConnected) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) TerminalSessionBus.append(buffer, read)
            }

            if (TerminalSessionBus.snapshot.value.state != TerminalConnectionState.ERROR) {
                TerminalSessionBus.markDisconnected("Powłoka SSH zakończona")
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (error: Throwable) {
            TerminalSessionBus.markError(error.readableMessage(host))
        } finally {
            TerminalSessionBus.detachWriter()
            closeTransport()
            jsch.removeAllIdentity()
            PendingSessionRegistry.remove(profile.id)
            activeProfile = null
        }
    }

    private fun disconnectFromNotification() {
        val profile = activeProfile
        connectionJob?.cancel()
        closeTransport()
        TerminalSessionBus.markDisconnected("Rozłączono z powiadomienia")
        profile?.id?.let { PendingSessionRegistry.remove(it) }
        activeProfile = null
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

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = Intent(this, TerminalSessionService::class.java)
            .setAction(ACTION_DISCONNECT)
        if (profileId != null) disconnectIntent.putExtra(EXTRA_PROFILE_ID, profileId)
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Client SSH działa w tle")
            .setContentText("$profileName • $status")
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Rozłącz",
                disconnectPendingIntent,
            )
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
            "Aktywne sesje terminala",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Utrzymuje połączenie SSH lub Telnet podczas blokady ekranu i po wyjściu z aplikacji"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun Throwable.readableMessage(host: String): String {
        val raw = message?.trim().orEmpty()
        return when {
            raw.contains("Auth fail", ignoreCase = true) -> "Nieprawidłowy login, hasło lub klucz SSH."
            raw.contains("reject HostKey", ignoreCase = true) -> "Klucz hosta SSH zmienił się. Połączenie zostało zablokowane."
            raw.contains("UnknownHostException", ignoreCase = true) ||
                raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Nie można znaleźć hosta: $host. Sprawdź internet, DNS albo literówkę w profilu."
            raw.contains("timeout", ignoreCase = true) -> "Przekroczono czas oczekiwania na połączenie."
            raw.isNotEmpty() -> raw
            else -> javaClass.simpleName
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val ACTION_DISCONNECT = "eu.blackserv.clientssh.action.DISCONNECT_SESSION"
        private const val CHANNEL_ID = "terminal_session"
        private const val NOTIFICATION_ID = 1001
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val CHANNEL_TIMEOUT_MS = 10_000
    }
}
