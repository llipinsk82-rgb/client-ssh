package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.FavoriteCommand
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.TerminalSettings
import eu.blackserv.clientssh.model.TextWrapMode
import eu.blackserv.clientssh.terminal.TerminalConnectionState
import eu.blackserv.clientssh.terminal.TerminalSessionBus
import eu.blackserv.clientssh.ui.terminal.toPlainTerminalText
import eu.blackserv.clientssh.ui.terminal.toTerminalAnnotatedString
import kotlinx.coroutines.delay

private val TerminalBackground = Color(0xFF020605)
private val TerminalPanel = Color(0xFF0B1410)
private val TerminalButton = Color(0xFF16231B)
private val TerminalGreen = Color(0xFF62D58A)
private val TerminalText = Color(0xFFDCE9DF)

private data class TerminalShortcut(
    val label: String,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    profile: HostProfile,
    favorites: List<FavoriteCommand>,
    terminalSettings: TerminalSettings,
    onTerminalSettingsChange: (TerminalSettings) -> Unit,
    onSaveFavorite: (FavoriteCommand) -> Unit,
    onDeleteFavorite: (FavoriteCommand) -> Unit,
    onMoveFavoriteUp: (FavoriteCommand) -> Unit,
    onMoveFavoriteDown: (FavoriteCommand) -> Unit,
    onSaveLog: (String, String) -> Unit,
    onClose: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
) {
    val session by TerminalSessionBus.snapshot.collectAsState()
    var fullscreen by remember { mutableStateOf(false) }
    var wrapMode by remember { mutableStateOf(TextWrapMode.WRAP) }
    var command by remember { mutableStateOf("") }
    var showFavorites by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    var connectedOnce by remember(profile.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val plainOutput = remember(session.output) { session.output.toPlainTerminalText() }
    val annotatedOutput = remember(session.output) { session.output.toTerminalAnnotatedString(TerminalText) }
    val controlsEnabled = session.state == TerminalConnectionState.CONNECTED

    LaunchedEffect(plainOutput.length) { verticalScroll.scrollTo(verticalScroll.maxValue) }
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    LaunchedEffect(terminalSettings.keepScreenAwake, controlsEnabled) {
        onKeepScreenAwakeChange(terminalSettings.keepScreenAwake && controlsEnabled)
    }
    DisposableEffect(Unit) {
        onDispose { onKeepScreenAwakeChange(false) }
    }
    LaunchedEffect(session.profileId, session.state) {
        if (session.profileId != profile.id) return@LaunchedEffect
        when (session.state) {
            TerminalConnectionState.CONNECTED -> connectedOnce = true
            TerminalConnectionState.DISCONNECTED -> if (connectedOnce) {
                delay(750)
                onFullscreenChange(false)
                onKeepScreenAwakeChange(false)
                onClose()
            }
            else -> Unit
        }
    }

    fun sendCommand(text: String) {
        if (text.isBlank() || !controlsEnabled) return
        TerminalSessionBus.send(text + "\r")
        command = ""
    }

    fun sendRaw(text: String) {
        if (!controlsEnabled) return
        TerminalSessionBus.send(text)
    }

    fun sendRaw(bytes: ByteArray) {
        if (!controlsEnabled) return
        TerminalSessionBus.send(bytes)
    }

    val shortcuts = buildList {
        favorites.forEach { favorite ->
            add(
                TerminalShortcut(favorite.name, controlsEnabled) {
                    if (favorite.runImmediately) sendCommand(favorite.command) else command = favorite.command
                },
            )
        }
        add(TerminalShortcut("ENTER", controlsEnabled) { sendRaw("\r") })
        add(TerminalShortcut("CTRL+C", controlsEnabled) { sendRaw(byteArrayOf(3)) })
        add(TerminalShortcut("TAB", controlsEnabled) { sendRaw("\t") })
        add(TerminalShortcut("CTRL+D", controlsEnabled) { sendRaw(byteArrayOf(4)) })
        add(TerminalShortcut("↑", controlsEnabled) { sendRaw("\u001B[A") })
        add(TerminalShortcut("↓", controlsEnabled) { sendRaw("\u001B[B") })
        add(TerminalShortcut("←", controlsEnabled) { sendRaw("\u001B[D") })
        add(TerminalShortcut("→", controlsEnabled) { sendRaw("\u001B[C") })
        add(TerminalShortcut("ESC", controlsEnabled) { sendRaw(byteArrayOf(27)) })
        add(TerminalShortcut("BUF CLEAR", true) { TerminalSessionBus.clearLocalBuffer() })
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = { TextButton(onClick = onClose) { Text("Wstecz") } },
                    actions = {
                        IconButton(onClick = { showHealth = !showHealth }) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = "Health")
                        }
                        IconButton(onClick = { showFavorites = true }) {
                            Icon(Icons.Default.Favorite, contentDescription = "Ulubione")
                        }
                        IconButton(onClick = {
                            wrapMode = if (wrapMode == TextWrapMode.WRAP) TextWrapMode.NO_WRAP else TextWrapMode.WRAP
                        }) {
                            Icon(Icons.Default.WrapText, contentDescription = wrapMode.label)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(plainOutput)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopiuj bufor")
                        }
                        IconButton(onClick = { onSaveLog("${profile.name}.log", plainOutput) }) {
                            Icon(Icons.Default.Save, contentDescription = "Zapisz log")
                        }
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Pełny ekran")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalBackground)
                .imePadding(),
        ) {
            ConnectionStatusBar(
                status = session.statusText,
                connected = session.state == TerminalConnectionState.CONNECTED,
                keepScreenAwake = terminalSettings.keepScreenAwake,
                onToggleKeepScreenAwake = {
                    onTerminalSettingsChange(
                        terminalSettings.copy(keepScreenAwake = !terminalSettings.keepScreenAwake),
                    )
                },
            )

            if (showHealth) HealthCard(profile)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(verticalScroll)
                    .then(
                        if (wrapMode == TextWrapMode.NO_WRAP) Modifier.horizontalScroll(horizontalScroll)
                        else Modifier,
                    )
                    .padding(10.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = annotatedOutput,
                        fontFamily = FontFamily.Monospace,
                        softWrap = wrapMode == TextWrapMode.WRAP,
                    )
                }
            }

            if (fullscreen) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { fullscreen = false }) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Wyłącz pełny ekran", tint = Color.White)
                    }
                }
            }

            TerminalKeyBar(shortcuts)

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Komenda…") },
                    singleLine = true,
                    enabled = controlsEnabled,
                )
                Button(
                    enabled = controlsEnabled && command.isNotBlank(),
                    onClick = { sendCommand(command) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalGreen,
                        contentColor = Color(0xFF031008),
                    ),
                ) { Text("Wyślij") }
            }
        }
    }

    if (showFavorites) {
        FavoritesDialog(
            favorites = favorites,
            onDismiss = { showFavorites = false },
            onSave = onSaveFavorite,
            onDelete = onDeleteFavorite,
            onMoveUp = onMoveFavoriteUp,
            onMoveDown = onMoveFavoriteDown,
        )
    }
}

@Composable
private fun ConnectionStatusBar(
    status: String,
    connected: Boolean,
    keepScreenAwake: Boolean,
    onToggleKeepScreenAwake: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = TerminalPanel,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (connected) "●" else "○", color = if (connected) TerminalGreen else Color(0xFF9AA7A0))
            Text(
                text = status,
                modifier = Modifier.weight(1f),
                color = TerminalText,
                fontFamily = FontFamily.Monospace,
            )
            IconButton(onClick = onToggleKeepScreenAwake) {
                Text(
                    text = if (keepScreenAwake) "☀" else "○",
                    color = if (keepScreenAwake) TerminalGreen else Color(0xFF9AA7A0),
                )
            }
        }
    }
}

@Composable
private fun TerminalKeyBar(shortcuts: List<TerminalShortcut>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(TerminalPanel).padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(shortcuts) { shortcut ->
            Button(
                enabled = shortcut.enabled,
                onClick = shortcut.action,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalButton,
                    contentColor = TerminalGreen,
                    disabledContainerColor = TerminalButton.copy(alpha = 0.55f),
                    disabledContentColor = TerminalGreen.copy(alpha = 0.45f),
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(shortcut.label) }
        }
    }
}

@Composable
private fun HealthCard(profile: HostProfile) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("Health • ${profile.name}")
            Text("CPU —   RAM —   LOAD —   DYSK —   PING —")
            Text("Dane pojawią się po podłączeniu poleceń diagnostycznych.")
        }
    }
}
