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
import eu.blackserv.clientssh.model.TextWrapMode
import eu.blackserv.clientssh.terminal.TerminalConnectionState
import eu.blackserv.clientssh.terminal.TerminalSessionBus

private val TerminalBackground = Color(0xFF020605)
private val TerminalPanel = Color(0xFF0B1410)
private val TerminalButton = Color(0xFF16231B)
private val TerminalGreen = Color(0xFF62D58A)
private val TerminalText = Color(0xFFDCE9DF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    profile: HostProfile,
    favorites: List<FavoriteCommand>,
    onSaveFavorite: (FavoriteCommand) -> Unit,
    onDeleteFavorite: (FavoriteCommand) -> Unit,
    onSaveLog: (String, String) -> Unit,
    onClose: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
) {
    val session by TerminalSessionBus.snapshot.collectAsState()
    var fullscreen by remember { mutableStateOf(false) }
    var wrapMode by remember { mutableStateOf(TextWrapMode.WRAP) }
    var command by remember { mutableStateOf("") }
    var showFavorites by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val visibleOutput = remember(session.output) { session.output.withoutAnsiControlCodes() }

    LaunchedEffect(visibleOutput.length) { verticalScroll.scrollTo(verticalScroll.maxValue) }
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }

    fun sendCommand(text: String) {
        if (text.isEmpty()) return
        TerminalSessionBus.send(text + "\r")
        command = ""
    }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(profile.name) },
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
                        IconButton(onClick = { clipboard.setText(AnnotatedString(visibleOutput)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopiuj bufor")
                        }
                        IconButton(onClick = { onSaveLog("${profile.name}.log", visibleOutput) }) {
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
                        text = visibleOutput,
                        color = TerminalText,
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

            TerminalKeyBar { key ->
                when (key) {
                    "ENTER" -> TerminalSessionBus.send("\r")
                    "CTRL+C" -> TerminalSessionBus.send(byteArrayOf(3))
                    "CTRL+D" -> TerminalSessionBus.send(byteArrayOf(4))
                    "TAB" -> TerminalSessionBus.send("\t")
                    "ESC" -> TerminalSessionBus.send(byteArrayOf(27))
                    "↑" -> TerminalSessionBus.send("\u001B[A")
                    "↓" -> TerminalSessionBus.send("\u001B[B")
                    "←" -> TerminalSessionBus.send("\u001B[D")
                    "→" -> TerminalSessionBus.send("\u001B[C")
                    "clear" -> sendCommand("clear")
                    "sudo -i" -> sendCommand("sudo -i")
                    "BUF CLEAR" -> TerminalSessionBus.clearLocalBuffer()
                }
            }

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
                )
                Button(
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
            onInsert = {
                command = it.command
                showFavorites = false
            },
            onRun = {
                sendCommand(it.command)
                showFavorites = false
            },
            onSave = onSaveFavorite,
            onDelete = onDeleteFavorite,
        )
    }
}

@Composable
private fun ConnectionStatusBar(status: String, connected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = TerminalPanel,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (connected) "●" else "○", color = if (connected) TerminalGreen else Color(0xFF9AA7A0))
            Text(status, color = TerminalText, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun TerminalKeyBar(onKey: (String) -> Unit) {
    val keys = listOf("ENTER", "CTRL+C", "TAB", "CTRL+D", "↑", "↓", "←", "→", "ESC", "clear", "sudo -i", "BUF CLEAR")
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(TerminalPanel).padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(keys) { key ->
            Button(
                onClick = { onKey(key) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalButton,
                    contentColor = TerminalGreen,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(key) }
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

private fun String.withoutAnsiControlCodes(): String = replace(
    Regex("\\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])"),
    "",
)
