package eu.blackserv.clientssh

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.blackserv.clientssh.model.AppSettings
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.FavoriteCommand
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.TerminalSettings
import eu.blackserv.clientssh.service.TerminalSessionService
import eu.blackserv.clientssh.storage.LocalAppStore
import eu.blackserv.clientssh.terminal.PendingSessionRegistry
import eu.blackserv.clientssh.terminal.TerminalConnectionState
import eu.blackserv.clientssh.terminal.TerminalSessionBus
import eu.blackserv.clientssh.ui.screens.HistoryScreen
import eu.blackserv.clientssh.ui.screens.ProfileEditorDialog
import eu.blackserv.clientssh.ui.screens.ProfilesScreen
import eu.blackserv.clientssh.ui.screens.SettingsScreen
import eu.blackserv.clientssh.ui.screens.SftpScreen
import eu.blackserv.clientssh.ui.screens.StartupScreen
import eu.blackserv.clientssh.ui.screens.TerminalScreen
import eu.blackserv.clientssh.ui.screens.UpdateDialog
import eu.blackserv.clientssh.ui.theme.AppBackdrop
import eu.blackserv.clientssh.ui.theme.ClientSshTheme
import eu.blackserv.clientssh.ui.theme.LocalAppSkin
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private var pendingLogContent: String = ""
    private val openActiveTerminalRequest = MutableStateFlow(0L)

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

        val openedFromSessionNotification = intent?.action == ACTION_OPEN_ACTIVE_TERMINAL
        if (openedFromSessionNotification) requestOpenActiveTerminal()

        setContent {
            val context = LocalContext.current
            val appStore = remember(context.applicationContext) {
                LocalAppStore(context.applicationContext)
            }
            var appSettings by remember(appStore) {
                mutableStateOf(appStore.loadAppSettings())
            }
            var showStartup by remember {
                mutableStateOf(savedInstanceState == null && !openedFromSessionNotification)
            }
            val openRequest by openActiveTerminalRequest.collectAsState()

            if (showStartup) {
                StartupScreen(onFinished = { showStartup = false })
            } else {
                ClientSshTheme(
                    skin = appSettings.skin,
                    darkTheme = true,
                ) {
                    ClientSshApp(
                        appStore = appStore,
                        appSettings = appSettings,
                        openActiveTerminalRequest = openRequest,
                        onAppSettingsChange = { updated ->
                            appSettings = updated
                            appStore.saveAppSettings(updated)
                        },
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_ACTIVE_TERMINAL) requestOpenActiveTerminal()
    }

    private fun requestOpenActiveTerminal() {
        openActiveTerminalRequest.value = System.nanoTime()
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
    data object History : Destination
    data object Settings : Destination
    data class Terminal(val profile: HostProfile) : Destination
    data class Sftp(val profile: HostProfile) : Destination
}

private data class MainNavItem(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun ClientSshApp(
    appStore: LocalAppStore,
    appSettings: AppSettings,
    openActiveTerminalRequest: Long,
    onAppSettingsChange: (AppSettings) -> Unit,
    onSessionStarted: (HostProfile) -> Unit,
    onSessionStopped: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onSaveLog: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val profiles = remember(appStore) {
        mutableStateListOf<HostProfile>().also { it.addAll(appStore.loadProfiles()) }
    }
    val favorites = remember(appStore) {
        mutableStateListOf<FavoriteCommand>().also { it.addAll(appStore.loadFavorites()) }
    }
    val session by TerminalSessionBus.snapshot.collectAsState()
    var terminalSettings by remember(appStore) {
        mutableStateOf(appStore.loadTerminalSettings())
    }
    var destination by remember { mutableStateOf<Destination>(Destination.Profiles) }
    var editedProfile by remember { mutableStateOf<HostProfile?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showUpdater by remember { mutableStateOf(false) }

    val sessionIsActive = session.state == TerminalConnectionState.CONNECTING ||
        session.state == TerminalConnectionState.CONNECTED
    val activeProfile = profiles.firstOrNull { it.id == session.profileId }

    LaunchedEffect(
        openActiveTerminalRequest,
        session.profileId,
        session.state,
        profiles.size,
    ) {
        if (openActiveTerminalRequest != 0L && sessionIsActive && activeProfile != null) {
            destination = Destination.Terminal(activeProfile)
        }
    }

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
        Destination.Profiles -> MainShell(
            current = current,
            onSelect = { destination = it },
        ) {
            ProfilesScreen(
                profiles = profiles,
                activeProfileId = session.profileId.takeIf { sessionIsActive },
                activeSessionStatus = session.statusText,
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
                onConnect = { profile ->
                    if (sessionIsActive && session.profileId == profile.id) {
                        destination = Destination.Terminal(profile)
                    } else {
                        onSessionStarted(profile)
                        destination = Destination.Terminal(profile)
                    }
                },
                onDisconnectActiveSession = onSessionStopped,
                onOpenSftp = { destination = Destination.Sftp(it) },
                onCheckUpdates = { showUpdater = true },
            )
        }

        Destination.History -> MainShell(
            current = current,
            onSelect = { destination = it },
        ) {
            HistoryScreen()
        }

        Destination.Settings -> MainShell(
            current = current,
            onSelect = { destination = it },
        ) {
            SettingsScreen(
                appSettings = appSettings,
                terminalSettings = terminalSettings,
                onAppSettingsChange = onAppSettingsChange,
                onTerminalSettingsChange = ::saveTerminalSettings,
                onCheckUpdates = { showUpdater = true },
            )
        }

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

@Composable
private fun MainShell(
    current: Destination,
    onSelect: (Destination) -> Unit,
    content: @Composable () -> Unit,
) {
    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                MainNavigationBar(
                    current = current,
                    onSelect = onSelect,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MainNavigationBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    val skin = LocalAppSkin.current
    val items = listOf(
        MainNavItem(Destination.Profiles, "Serwery", Icons.Default.Storage),
        MainNavItem(Destination.History, "Historia", Icons.Default.History),
        MainNavItem(Destination.Settings, "Ustawienia", Icons.Default.Settings),
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(
            alpha = if (skin == AppSkin.NEON) 0.94f else 1f,
        ),
        tonalElevation = if (skin == AppSkin.NEON) 10.dp else 0.dp,
    ) {
        items.forEach { item ->
            val selected = current == item.destination
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.destination) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = if (skin == AppSkin.NEON) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = if (skin == AppSkin.NEON) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun String.sanitizeFilename(): String =
    replace(Regex("[^a-zA-Z0-9._-]+"), "_").ifBlank { "terminal.log" }
