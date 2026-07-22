package eu.blackserv.clientssh.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.sftp.SftpClient
import eu.blackserv.clientssh.sftp.SftpEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SftpBackground = Color(0xFF07100D)
private val SftpPanel = Color(0xFF101A16)
private val SftpRow = Color(0xFF0D1713)
private val SftpStroke = Color(0xFF26372F)
private val SftpAccent = Color(0xFF62D58A)
private val SftpMuted = Color(0xFFA7B5AD)
private val SftpDanger = Color(0xFFE88989)

private sealed interface SftpDialogState {
    data object None : SftpDialogState
    data object NewFolder : SftpDialogState
    data class Actions(val entry: SftpEntry) : SftpDialogState
    data class Rename(val entry: SftpEntry) : SftpDialogState
    data class ConfirmDelete(val entry: SftpEntry) : SftpDialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(profile: HostProfile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(profile.id) {
        SftpClient(File(context.filesDir, "ssh/known_hosts"))
    }

    var path by remember(profile.id) { mutableStateOf("/") }
    var entries by remember(profile.id) { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var status by remember(profile.id) { mutableStateOf("Łączenie SFTP…") }
    var busy by remember(profile.id) { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<SftpDialogState>(SftpDialogState.None) }
    var pendingDownload by remember { mutableStateOf<SftpEntry?>(null) }

    fun updateFrom(result: Pair<String, List<SftpEntry>>) {
        path = result.first
        entries = result.second.sortedWith(compareByDescending<SftpEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun runSftp(label: String, block: suspend () -> Unit) {
        busy = true
        status = label
        scope.launch {
            runCatching { block() }
                .onFailure { error -> status = client.readableError(error, profile) }
            busy = false
        }
    }

    fun refresh() = runSftp("Odświeżanie…") {
        val result = withContext(Dispatchers.IO) { client.currentPath() to client.listCurrent() }
        updateFrom(result)
        status = "SFTP • $path"
    }

    fun openEntry(entry: SftpEntry) {
        if (!entry.isDirectory || busy) return
        runSftp("Otwieranie ${entry.name}…") {
            val result = withContext(Dispatchers.IO) { client.openDirectory(entry.path) }
            updateFrom(result)
            status = "SFTP • ${result.first}"
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null) {
            runSftp("Pobieranie ${entry.name}…") {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        client.download(entry.path, output)
                    } ?: error("Nie można otworzyć pliku docelowego.")
                }
                status = "Pobrano: ${entry.name}"
            }
        }
    }

    fun downloadEntry(entry: SftpEntry) {
        if (entry.isDirectory || busy) return
        pendingDownload = entry
        downloadLauncher.launch(entry.name)
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = context.displayName(uri).ifBlank { "upload.bin" }
            runSftp("Wysyłanie $fileName…") {
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        client.upload(input, fileName)
                    } ?: error("Nie można otworzyć pliku źródłowego.")
                }
                updateFrom(result)
                status = "Wysłano: $fileName"
            }
        }
    }

    LaunchedEffect(profile.id) {
        runCatching {
            withContext(Dispatchers.IO) {
                client.connect(profile)
                client.currentPath() to client.listCurrent()
            }
        }.onSuccess { result ->
            updateFrom(result)
            status = "SFTP • ${result.first}"
        }.onFailure { error ->
            status = client.readableError(error, profile)
        }
        busy = false
    }

    DisposableEffect(profile.id) {
        onDispose { client.disconnect() }
    }

    Scaffold(
        containerColor = SftpBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A1511),
                    titleContentColor = Color(0xFFE6F0EA),
                    navigationIconContentColor = SftpAccent,
                    actionIconContentColor = SftpAccent,
                ),
                title = {
                    Column {
                        Text("SFTP • ${profile.name}", fontWeight = FontWeight.Bold)
                        Text(
                            path,
                            color = SftpMuted,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wstecz") } },
                actions = {
                    IconButton(enabled = !busy, onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
                    }
                    IconButton(enabled = !busy, onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Wyślij plik")
                    }
                    IconButton(enabled = !busy, onClick = { dialog = SftpDialogState.NewFolder }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Nowy katalog")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SftpBackground),
        ) {
            SftpCommanderHeader(
                status = status,
                path = path,
                busy = busy,
                onParent = {
                    runSftp("Przejście wyżej…") {
                        val result = withContext(Dispatchers.IO) { client.parentDirectory() }
                        updateFrom(result)
                        status = "SFTP • ${result.first}"
                    }
                },
                onRefresh = { refresh() },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(entries, key = { it.path }) { entry ->
                    SftpCompactRow(
                        entry = entry,
                        busy = busy,
                        onOpen = { openEntry(entry) },
                        onDownload = { downloadEntry(entry) },
                        onActions = { dialog = SftpDialogState.Actions(entry) },
                    )
                }
            }
        }
    }

    when (val current = dialog) {
        SftpDialogState.None -> Unit
        SftpDialogState.NewFolder -> TextInputDialog(
            title = "Nowy katalog",
            label = "Nazwa katalogu",
            initial = "",
            onDismiss = { dialog = SftpDialogState.None },
            onConfirm = { name ->
                dialog = SftpDialogState.None
                runSftp("Tworzenie katalogu…") {
                    val result = withContext(Dispatchers.IO) { client.mkdir(name) }
                    updateFrom(result)
                    status = "Utworzono katalog: $name"
                }
            },
        )
        is SftpDialogState.Actions -> SftpActionsDialog(
            entry = current.entry,
            onDismiss = { dialog = SftpDialogState.None },
            onOpen = {
                dialog = SftpDialogState.None
                openEntry(current.entry)
            },
            onDownload = {
                dialog = SftpDialogState.None
                downloadEntry(current.entry)
            },
            onRename = { dialog = SftpDialogState.Rename(current.entry) },
            onDelete = { dialog = SftpDialogState.ConfirmDelete(current.entry) },
        )
        is SftpDialogState.Rename -> TextInputDialog(
            title = "Zmień nazwę",
            label = "Nowa nazwa",
            initial = current.entry.name,
            onDismiss = { dialog = SftpDialogState.None },
            onConfirm = { name ->
                dialog = SftpDialogState.None
                runSftp("Zmiana nazwy…") {
                    val result = withContext(Dispatchers.IO) { client.rename(current.entry.path, name) }
                    updateFrom(result)
                    status = "Zmieniono nazwę na: $name"
                }
            },
        )
        is SftpDialogState.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = SftpDialogState.None },
            title = { Text("Usunąć?") },
            text = { Text("${current.entry.name}\nTej operacji nie da się cofnąć.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SftpDanger),
                    onClick = {
                        dialog = SftpDialogState.None
                        runSftp("Usuwanie…") {
                            val result = withContext(Dispatchers.IO) { client.delete(current.entry) }
                            updateFrom(result)
                            status = "Usunięto: ${current.entry.name}"
                        }
                    },
                ) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { dialog = SftpDialogState.None }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun SftpCommanderHeader(
    status: String,
    path: String,
    busy: Boolean,
    onParent: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        color = SftpPanel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SftpStroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(status, color = Color(0xFFE6F0EA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(path, color = SftpMuted, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(enabled = !busy && path != "/", onClick = onParent) { Text("..") }
            TextButton(enabled = !busy, onClick = onRefresh) { Text("R") }
        }
    }
}

@Composable
private fun SftpCompactRow(
    entry: SftpEntry,
    busy: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onActions: () -> Unit,
) {
    val icon = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile
    val iconTint = if (entry.isDirectory) SftpAccent else SftpMuted
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = SftpRow,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, SftpStroke.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier
                .height(46.dp)
                .clickable(enabled = !busy) { if (entry.isDirectory) onOpen() else onDownload() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = Color(0xFFE6F0EA),
                    fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.details(),
                    color = SftpMuted,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(enabled = !busy, onClick = onActions, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Akcje", tint = SftpMuted)
            }
        }
    }
}

@Composable
private fun SftpActionsDialog(
    entry: SftpEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.details(), color = SftpMuted, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                if (entry.isDirectory) {
                    TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Otwórz katalog")
                    }
                } else {
                    TextButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pobierz plik")
                    }
                }
                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Zmień nazwę")
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = SftpDanger)
                    Spacer(Modifier.width(8.dp))
                    Text("Usuń", color = SftpDanger)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

private fun SftpEntry.details(): String {
    val sizeText = if (isDirectory) "DIR" else formatBytes(size)
    return listOf(sizeText, permissions, modified).filter { it.isNotBlank() }.joinToString(" • ")
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun android.content.Context.displayName(uri: Uri): String {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index).orEmpty()
    }
    return uri.lastPathSegment.orEmpty().substringAfterLast('/')
}
