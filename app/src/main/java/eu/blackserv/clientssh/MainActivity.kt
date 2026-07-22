package eu.blackserv.clientssh

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.blackserv.clientssh.model.FavoriteCommand
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.TerminalSettings
import eu.blackserv.clientssh.service.TerminalSessionService
import eu.blackserv.clientssh.storage.LocalAppStore
import eu.blackserv.clientssh.terminal.PendingSessionRegistry
import eu.blackserv.clientssh.terminal.TerminalSessionBus
import eu.blackserv.clientssh.ui.screens.ProfileEditorDialog
import eu.blackserv.clientssh.ui.screens.ProfilesScreen
import eu.blackserv.clientssh.ui.screens.SftpScreen
import eu.blackserv.clientssh.ui.screens.TerminalScreen
import eu.blackserv.clientssh.ui.screens.UpdateDialog
import eu.blackserv.clientssh.ui.theme.ClientSshTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var pendingLogContent: String = ""

    private val saveLogLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(pendingLogContent)
            }
        }
        pendingLogContent = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ClientSshTheme(darkTheme = true) {
                ClientSshApp(
                    onSessionStarted = ::startSessionService,
                    onSessionStopped = ::stopSessionService,
                    onFullscreenChange = ::setFullscreen,
                    onKeepScreenAwakeChange = ::setKeepScreenAwake,
                    onSaveLog = { filename, content ->
                        pendingLogContent = content
                        saveLogLauncher.launch(filename.sanitizeFilename())
                    },
                )
            }
        }
    }

    private fun startSessionService(profile: HostProfile) {
        PendingSessionRegistry.put(profile)
        TerminalSessionBus.begin(profile)
        val intent = Intent(this, TerminalSessionService::class.java)
            .putExtra(TerminalSessionService.EXTRA_PROFILE_ID, profile.id)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopSessionService() {
        stopService(Intent(this, TerminalSessionService::class.java))
    }

    private fun setFullscreen(enabled: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setKeepScreenAwake(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        const val ACTION_OPEN_ACTIVE_TERMINAL = "eu.blackserv.clientssh.action.OPEN_ACTIVE_TERMINAL"
    }
}

private sealed interface Destination {
    data object Profiles : Destination
    data class Terminal(val profile: HostProfile) : Destination
    data class Sftp(val profile: HostProfile) : Destination
}

@Composable
private fun ClientSshApp(
    onSessionStarted: (HostProfile) -> Unit,
    onSessionStopped: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onSaveLog: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val appStore = remember(context.applicationContext) { LocalAppStore(context.applicationContext) }
    val profiles = remember(appStore) { mutableStateListOf<HostProfile>().also { it.addAll(appStore.loadProfiles()) } }
    val favorites = remember(appStore) { mutableStateListOf<FavoriteCommand>().also { it.addAll(appStore.loadFavorites()) } }
    var terminalSettings by remember(appStore) { mutableStateOf(appStore.loadTerminalSettings()) }
    var destination by remember { mutableStateOf<Destination>(Destination.Profiles) }
    var editedProfile by remember { mutableStateOf<HostProfile?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showUpdater by remember { mutableStateOf(false) }

    fun saveProfiles() = appStore.saveProfiles(profiles)
    fun saveFavorites() = appStore.saveFavorites(favorites)

    fun saveTerminalSettings(settings: TerminalSettings) {
        terminalSettings = settings
        appStore.saveTerminalSettings(settings)
    }

    fun moveFavorite(favorite: FavoriteCommand, direction: Int) {
        val index = favorites.indexOfFirst { it.id == favorite.id }
        val target = index + direction
        if (index < 0 || target !in favorites.indices) return
        val item = favorites.removeAt(index)
        favorites.add(target, item)
        saveFavorites()
    }

    when (val current = destination) {
        Destination.Profiles -> ProfilesScreen(
            profiles = profiles,
            onAdd = {
                editedProfile = null
                showProfileEditor = true
            },
            onEdit = {
                editedProfile = it
                showProfileEditor = true
            },
            onClone = { profile ->
                editedProfile = profile.copy(
                    id = UUID.randomUUID().toString(),
                    name = profile.name.ifBlank { profile.host }.let { "$it kopia" },
                )
                showProfileEditor = true
            },
            onDelete = {
                profiles.remove(it)
                saveProfiles()
            },
            onConnect = {
                onSessionStarted(it)
                destination = Destination.Terminal(it)
            },
            onOpenSftp = { destination = Destination.Sftp(it) },
            onCheckUpdates = { showUpdater = true },
        )

        is Destination.Terminal -> TerminalScreen(
            profile = current.profile,
            favorites = favorites,
            terminalSettings = terminalSettings,
            onTerminalSettingsChange = ::saveTerminalSettings,
            onSaveFavorite = { updated ->
                val index = favorites.indexOfFirst { it.id == updated.id }
                if (index >= 0) favorites[index] = updated else favorites.add(updated)
                saveFavorites()
            },
            onDeleteFavorite = {
                favorites.remove(it)
                saveFavorites()
            },
            onMoveFavoriteUp = { moveFavorite(it, -1) },
            onMoveFavoriteDown = { moveFavorite(it, 1) },
            onSaveLog = onSaveLog,
            onClose = {
                onFullscreenChange(false)
                onKeepScreenAwakeChange(false)
                destination = Destination.Profiles
            },
            onFullscreenChange = onFullscreenChange,
            onKeepScreenAwakeChange = onKeepScreenAwakeChange,
        )

        is Destination.Sftp -> SftpScreen(
            profile = current.profile,
            onBack = { destination = Destination.Profiles },
        )
    }

    if (showProfileEditor) {
        ProfileEditorDialog(
            existing = editedProfile,
            onDismiss = { showProfileEditor = false },
            onSave = { profile ->
                val index = profiles.indexOfFirst { it.id == profile.id }
                if (index >= 0) profiles[index] = profile else profiles.add(profile)
                saveProfiles()
                showProfileEditor = false
            },
        )
    }

    if (showUpdater) {
        UpdateDialog(context = context, onDismiss = { showUpdater = false })
    }
}

private fun String.sanitizeFilename(): String =
    replace(Regex("[^a-zA-Z0-9._-]+"), "_").ifBlank { "terminal.log" }
