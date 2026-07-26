package eu.blackserv.clientssh.backup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.MainActivity
import eu.blackserv.clientssh.storage.LocalAppStore
import eu.blackserv.clientssh.ui.theme.ClientSshTheme
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileBackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appStore = LocalAppStore(applicationContext)
        setContent {
            ClientSshTheme(skin = appStore.loadAppSettings().skin, darkTheme = true) {
                ProfileBackupScreen(
                    appStore = appStore,
                    onClose = ::finish,
                    onImportCompleted = ::restartApplication,
                )
            }
        }
    }

    private fun restartApplication() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }
}

private enum class PasswordDialogMode { EXPORT, IMPORT }

private data class OperationMessage(
    val text: String,
    val isError: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBackupScreen(
    appStore: LocalAppStore,
    onClose: () -> Unit,
    onImportCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profileCount by remember { mutableStateOf(appStore.loadProfiles().size) }
    var dialogMode by remember { mutableStateOf<PasswordDialogMode?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<OperationMessage?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri ->
        val encrypted = pendingExport
        pendingExport = null
        if (encrypted == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            encrypted.fill(0)
            message = OperationMessage("Eksport anulowany.", isError = false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        output.write(encrypted)
                        output.flush()
                    } ?: error("Brak dostępu do wybranego pliku")
                }.isSuccess
            }
            encrypted.fill(0)
            busy = false
            message = if (saved) {
                OperationMessage(
                    "Zaszyfrowany backup zapisany. Przechowuj plik i hasło osobno.",
                    isError = false,
                )
            } else {
                OperationMessage("Nie udało się zapisać backupu.", isError = true)
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            message = OperationMessage("Import anulowany.", isError = false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBoundedBackup(ProfileBackupCodec.MAX_CONTAINER_BYTES)
                    } ?: error("Brak dostępu do wybranego pliku")
                }.getOrNull()
            }
            busy = false
            if (loaded == null) {
                message = OperationMessage("Nie udało się odczytać backupu.", isError = true)
            } else {
                pendingImport?.fill(0)
                pendingImport = loaded
                dialogMode = PasswordDialogMode.IMPORT
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bezpieczna migracja profili") },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Text("Szyfrowany kontener", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Backup zawiera profile wraz z hasłami, prywatnymi kluczami SSH i passphrase. " +
                            "Plik jest szyfrowany przed zapisaniem i nigdy nie powinien być wysyłany przez czat, Issue ani publiczny załącznik.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Profile w tej instalacji: $profileCount",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { dialogMode = PasswordDialogMode.EXPORT },
                enabled = !busy && profileCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Text("Utwórz zaszyfrowany backup", modifier = Modifier.padding(start = 8.dp))
            }

            Text(
                "Hasło backupu nie jest zapisywane przez aplikację. Utrata hasła oznacza brak możliwości odzyskania profili.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { openBackup.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream")) },
                enabled = !busy && profileCount == 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Text("Importuj backup do pustej instalacji", modifier = Modifier.padding(start = 8.dp))
            }

            if (profileCount > 0) {
                Text(
                    "Import jest zablokowany, ponieważ istnieją już profile. Aplikacja nie nadpisuje ich automatycznie.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Przetwarzanie lokalne…")
                }
            }

            message?.let { current ->
                Text(
                    current.text,
                    color = if (current.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    dialogMode?.let { mode ->
        BackupPasswordDialog(
            mode = mode,
            onDismiss = {
                if (!busy) {
                    dialogMode = null
                    if (mode == PasswordDialogMode.IMPORT) {
                        pendingImport?.fill(0)
                        pendingImport = null
                    }
                }
            },
            onConfirm = { password, confirmation ->
                dialogMode = null
                val passwordChars = password.toCharArray()
                scope.launch {
                    busy = true
                    when (mode) {
                        PasswordDialogMode.EXPORT -> {
                            if (password != confirmation) {
                                passwordChars.fill('\u0000')
                                busy = false
                                message = OperationMessage("Hasła backupu nie są identyczne.", isError = true)
                                return@launch
                            }
                            val result = withContext(Dispatchers.Default) {
                                runCatching {
                                    ProfileBackupCodec.encrypt(appStore.loadProfiles(), passwordChars)
                                }
                            }
                            passwordChars.fill('\u0000')
                            busy = false
                            result.onSuccess { encrypted ->
                                pendingExport?.fill(0)
                                pendingExport = encrypted
                                createBackup.launch(DEFAULT_BACKUP_FILENAME)
                            }.onFailure { error ->
                                message = OperationMessage(error.safeBackupMessage(), isError = true)
                            }
                        }

                        PasswordDialogMode.IMPORT -> {
                            val encrypted = pendingImport
                            if (encrypted == null) {
                                passwordChars.fill('\u0000')
                                busy = false
                                message = OperationMessage("Brak wybranego pliku backupu.", isError = true)
                                return@launch
                            }
                            val result = withContext(Dispatchers.Default) {
                                runCatching { ProfileBackupCodec.decrypt(encrypted, passwordChars) }
                            }
                            passwordChars.fill('\u0000')
                            result.onSuccess { profiles ->
                                val saved = withContext(Dispatchers.IO) {
                                    runCatching {
                                        check(appStore.loadProfiles().isEmpty()) {
                                            "Import wymaga pustej instalacji"
                                        }
                                        appStore.saveProfiles(profiles)
                                    }
                                }
                                encrypted.fill(0)
                                pendingImport = null
                                busy = false
                                if (saved.isSuccess) {
                                    profileCount = profiles.size
                                    message = OperationMessage(
                                        "Import zakończony. Aplikacja zostanie uruchomiona ponownie.",
                                        isError = false,
                                    )
                                    onImportCompleted()
                                } else {
                                    message = OperationMessage(
                                        "Nie udało się bezpiecznie zapisać zaimportowanych profili.",
                                        isError = true,
                                    )
                                }
                            }.onFailure { error ->
                                encrypted.fill(0)
                                pendingImport = null
                                busy = false
                                message = OperationMessage(error.safeBackupMessage(), isError = true)
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    mode: PasswordDialogMode,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val export = mode == PasswordDialogMode.EXPORT
    val canConfirm = password.length >= ProfileBackupCodec.MIN_PASSWORD_CHARS &&
        (!export || confirmation.length >= ProfileBackupCodec.MIN_PASSWORD_CHARS)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (export) "Hasło nowego backupu" else "Hasło backupu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Minimum ${ProfileBackupCodec.MIN_PASSWORD_CHARS} znaków. Nie używaj hasła klucza SSH ani hasła serwera.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło backupu") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (export) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Powtórz hasło") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    val submittedPassword = password
                    val submittedConfirmation = confirmation
                    password = ""
                    confirmation = ""
                    onConfirm(submittedPassword, submittedConfirmation)
                },
            ) { Text(if (export) "Szyfruj i zapisz" else "Odszyfruj i importuj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

internal fun InputStream.readBoundedBackup(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    try {
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) continue
            if (output.size() + read > maxBytes) {
                throw ProfileBackupException("Plik backupu przekracza dozwolony rozmiar.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    } finally {
        buffer.fill(0)
    }
}

private fun Throwable.safeBackupMessage(): String =
    (this as? ProfileBackupException)?.message
        ?: "Operacja backupu nie powiodła się. Istniejące profile nie zostały zmienione."

private const val BACKUP_MIME_TYPE = "application/vnd.blackserv.clientssh.backup"
private const val DEFAULT_BACKUP_FILENAME = "client-ssh-profiles.bsshbackup"
