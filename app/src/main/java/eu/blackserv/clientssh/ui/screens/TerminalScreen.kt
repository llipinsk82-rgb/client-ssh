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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var fullscreen by remember { mutableStateOf(false) }
    var wrapMode by remember { mutableStateOf(TextWrapMode.WRAP) }
    var command by remember { mutableStateOf("") }
    var showFavorites by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    var output by remember {
        mutableStateOf(
            buildString {
                appendLine("Client SSH 0.1.0")
                appendLine("Profil: ${profile.name} (${profile.protocol.label} ${profile.host}:${profile.port})")
                appendLine()
                appendLine("Interfejs terminala jest gotowy. Transport SSH/Telnet zostanie podłączony w kolejnym etapie.")
                append("${profile.username.ifBlank { "user" }}@${profile.host}:~$ ")
            },
        )
    }
    val clipboard = LocalClipboardManager.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    LaunchedEffect(output) { verticalScroll.scrollTo(verticalScroll.maxValue) }
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }

    fun execute(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        if (normalized == "exit") {
            onClose()
            return
        }
        output += "$normalized\n"
        if (normalized == "clear") {
            output = "${profile.username.ifBlank { "user" }}@${profile.host}:~$ "
        } else {
            output += "[prototyp] Oczekiwanie na transport ${profile.protocol.label}.\n"
            output += "${profile.username.ifBlank { "user" }}@${profile.host}:~$ "
        }
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
                        IconButton(onClick = { clipboard.setText(AnnotatedString(output)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopiuj bufor")
                        }
                        IconButton(onClick = { onSaveLog("${profile.name}.log", output) }) {
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
                .background(Color(0xFF02060B))
                .imePadding(),
        ) {
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
                        text = output,
                        color = Color(0xFFDDEAFF),
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
                    "ENTER" -> execute(command)
                    "CTRL+C" -> {
                        output += "^C\n${profile.username.ifBlank { "user" }}@${profile.host}:~$ "
                        command = ""
                    }
                    "CTRL+D" -> onClose()
                    "TAB" -> command += "\t"
                    "clear" -> execute("clear")
                    "sudo -i" -> execute("sudo -i")
                    "↑", "↓", "←", "→", "ESC" -> Unit
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
                Button(onClick = { execute(command) }) { Text("Wyślij") }
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
                execute(it.command)
                showFavorites = false
            },
            onSave = onSaveFavorite,
            onDelete = onDeleteFavorite,
        )
    }
}

@Composable
private fun TerminalKeyBar(onKey: (String) -> Unit) {
    val keys = listOf("ENTER", "CTRL+C", "TAB", "CTRL+D", "↑", "↓", "←", "→", "ESC", "clear", "sudo -i")
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0A1420)).padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(keys) { key -> Button(onClick = { onKey(key) }) { Text(key) } }
    }
}

@Composable
private fun HealthCard(profile: HostProfile) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("Health • ${profile.name}")
            Text("CPU —   RAM —   LOAD —   DYSK —   PING —")
            Text("Dane pojawią się po podłączeniu transportu.")
        }
    }
}
