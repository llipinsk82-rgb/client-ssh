package eu.blackserv.clientssh.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.terminal.normalizePrivateKeyText
import eu.blackserv.clientssh.terminal.validatePrivateKeyMaterial
import java.util.UUID

private const val MAX_PRIVATE_KEY_CHARS = 256 * 1024

@Composable
fun ProfileEditorDialog(
    existing: HostProfile? = null,
    isActiveProfile: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit = {},
    onDelete: (HostProfile) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember(existing?.id) { mutableStateOf(existing?.host.orEmpty()) }
    var username by remember(existing?.id) { mutableStateOf(existing?.username.orEmpty()) }
    var protocol by remember(existing?.id) { mutableStateOf(existing?.protocol ?: ConnectionProtocol.SSH) }
    var port by remember(existing?.id) { mutableStateOf((existing?.port ?: protocol.defaultPort).toString()) }
    var authentication by remember(existing?.id) {
        mutableStateOf(existing?.authenticationMethod ?: AuthenticationMethod.PASSWORD)
    }
    var password by remember(existing?.id) { mutableStateOf(existing?.password.orEmpty()) }
    var privateKey by remember(existing?.id) { mutableStateOf(existing?.privateKey.orEmpty()) }
    var privateKeyPassphrase by remember(existing?.id) { mutableStateOf(existing?.privateKeyPassphrase.orEmpty()) }
    var keyMessage by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val keyFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val text = reader.readText()
                require(text.length <= MAX_PRIVATE_KEY_CHARS) { "Plik klucza jest zbyt duży." }
                text.normalizePrivateKeyText()
            } ?: error("Nie można otworzyć wybranego pliku.")
        }.onSuccess { importedKey ->
            privateKey = importedKey
            keyError = null
            keyMessage = "Klucz wczytany z pliku."
        }.onFailure { error ->
            keyMessage = null
            keyError = error.message ?: "Nie udało się wczytać klucza."
        }
    }

    val portNumber = port.toIntOrNull()
    val credentialsValid = when (authentication) {
        AuthenticationMethod.PASSWORD -> password.isNotBlank()
        AuthenticationMethod.PRIVATE_KEY -> privateKey.isNotBlank()
        AuthenticationMethod.INTERACTIVE -> true
    }
    val formValid = name.isNotBlank() &&
        host.isNotBlank() &&
        portNumber?.let { it in 1..65535 } == true &&
        (protocol != ConnectionProtocol.SSH || username.isNotBlank()) &&
        credentialsValid

    fun buildProfile(id: String = existing?.id ?: UUID.randomUUID().toString()): HostProfile? {
        if (!formValid) return null
        val normalizedKey = if (authentication == AuthenticationMethod.PRIVATE_KEY) {
            privateKey.normalizePrivateKeyText()
        } else {
            ""
        }
        if (authentication == AuthenticationMethod.PRIVATE_KEY) {
            val validationError = validatePrivateKeyMaterial(normalizedKey, privateKeyPassphrase)
            if (validationError != null) {
                keyMessage = null
                keyError = validationError
                return null
            }
        }
        keyError = null
        return HostProfile(
            id = id,
            name = name.trim(),
            host = host.trim(),
            port = port.toInt(),
            username = username.trim(),
            protocol = protocol,
            authenticationMethod = authentication,
            password = if (authentication == AuthenticationMethod.PASSWORD) password else "",
            privateKey = normalizedKey,
            privateKeyPassphrase = if (authentication == AuthenticationMethod.PRIVATE_KEY) privateKeyPassphrase else "",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nowy profil" else "Edytuj profil") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectionProtocol.entries.forEach { option ->
                        FilterChip(
                            selected = protocol == option,
                            onClick = {
                                val oldDefault = protocol.defaultPort.toString()
                                protocol = option
                                if (port.isBlank() || port == oldDefault) port = option.defaultPort.toString()
                                if (option == ConnectionProtocol.TELNET && authentication == AuthenticationMethod.PRIVATE_KEY) {
                                    authentication = AuthenticationMethod.PASSWORD
                                }
                            },
                            label = { Text(option.label) },
                        )
                    }
                }

                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nazwa") }, singleLine = true)
                OutlinedTextField(host, { host = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Host lub IP") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(username, { username = it }, Modifier.weight(1f), label = { Text("Użytkownik") }, singleLine = true)
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.weight(.65f),
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                Text("Logowanie")
                AuthenticationMethod.entries
                    .filter { protocol == ConnectionProtocol.SSH || it != AuthenticationMethod.PRIVATE_KEY }
                    .forEach { option ->
                        FilterChip(
                            selected = authentication == option,
                            onClick = {
                                authentication = option
                                keyError = null
                                keyMessage = null
                            },
                            label = { Text(option.label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }

                when (authentication) {
                    AuthenticationMethod.PASSWORD -> OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hasło") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    AuthenticationMethod.PRIVATE_KEY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val pasted = clipboard.getText()?.text.orEmpty()
                                    when {
                                        pasted.isBlank() -> {
                                            keyMessage = null
                                            keyError = "Schowek jest pusty."
                                        }
                                        pasted.length > MAX_PRIVATE_KEY_CHARS -> {
                                            keyMessage = null
                                            keyError = "Klucz w schowku jest zbyt duży."
                                        }
                                        else -> {
                                            privateKey = pasted.normalizePrivateKeyText()
                                            keyError = null
                                            keyMessage = "Klucz wklejony ze schowka."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Wklej") }
                            OutlinedButton(
                                onClick = { keyFileLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Wybierz plik") }
                        }
                        Text("Obsługiwane: OpenSSH, PEM, PKCS#8 i PuTTY PPK.")
                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = {
                                privateKey = it
                                keyError = null
                                keyMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Klucz prywatny") },
                            minLines = 5,
                            maxLines = 9,
                            supportingText = { Text("Wklej pełną zawartość klucza lub wybierz plik.") },
                        )
                        OutlinedTextField(
                            value = privateKeyPassphrase,
                            onValueChange = {
                                privateKeyPassphrase = it
                                keyError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Hasło klucza — opcjonalne") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        keyMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        keyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    AuthenticationMethod.INTERACTIVE -> Text("Dane logowania wpiszesz w terminalu.")
                }

                Text("Dane logowania są chronione przez Android Keystore.")

                if (existing != null) {
                    OutlinedButton(
                        onClick = {
                            buildProfile(
                                id = UUID.randomUUID().toString(),
                            )?.let { clone ->
                                onClone(clone.copy(name = "${clone.name} kopia"))
                            }
                        },
                        enabled = formValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Klonuj profil") }

                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !isActiveProfile,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(if (isActiveProfile) "Najpierw rozłącz aktywną sesję" else "Usuń profil") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = formValid,
                onClick = { buildProfile()?.let(onSave) },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć profil?") },
            text = { Text("Profil „${existing.name}” zostanie trwale usunięty z tego urządzenia.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(existing)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}
