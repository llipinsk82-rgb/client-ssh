package eu.blackserv.clientssh.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.sftp.SftpClient
import eu.blackserv.clientssh.sftp.SftpEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SftpDialogState {
    data object None : SftpDialogState
    data object NewFolder : SftpDialogState
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
        entries = result.second
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
        topBar = {
            TopAppBar(
                title = { Text("SFTP • ${profile.name}") },
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
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(status, fontWeight = FontWeight.Bold)
                    Text(path, fontFamily = FontFamily.Monospace)
                    if (busy) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && path != "/", onClick = {
                            runSftp("Przejście wyżej…") {
                                val result = withContext(Dispatchers.IO) { client.parentDirectory() }
                                updateFrom(result)
                                status = "SFTP • ${result.first}"
                            }
                        }) { Text("↑ Wyżej") }
                        Button(enabled = !busy, onClick = { refresh() }) { Text("Odśwież") }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.path }) { entry ->
                    SftpEntryCard(
                        entry = entry,
                        busy = busy,
                        onOpen = {
                            runSftp("Otwieranie ${entry.name}…") {
                                val result = withContext(Dispatchers.IO) { client.openDirectory(entry.path) }
                                updateFrom(result)
                                status = "SFTP • ${result.first}"
                            }
                        },
                        onDownload = {
                            pendingDownload = entry
                            downloadLauncher.launch(entry.name)
                        },
                        onRename = { dialog = SftpDialogState.Rename(entry) },
                        onDelete = { dialog = SftpDialogState.ConfirmDelete(entry) },
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
                Button(onClick = {
                    dialog = SftpDialogState.None
                    runSftp("Usuwanie…") {
                        val result = withContext(Dispatchers.IO) { client.delete(current.entry) }
                        updateFrom(result)
                        status = "Usunięto: ${current.entry.name}"
                    }
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { dialog = SftpDialogState.None }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun SftpEntryCard(
    entry: SftpEntry,
    busy: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, fontWeight = FontWeight.Bold)
                    Text(entry.details(), fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.isDirectory) {
                    TextButton(enabled = !busy, onClick = onOpen) { Text("Otwórz") }
                } else {
                    TextButton(enabled = !busy, onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pobierz")
                    }
                }
                TextButton(enabled = !busy, onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Zmień")
                }
                TextButton(enabled = !busy, onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Usuń")
                }
            }
        }
    }
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
