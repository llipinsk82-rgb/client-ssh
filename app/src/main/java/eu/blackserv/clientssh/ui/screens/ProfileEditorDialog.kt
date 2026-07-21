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
    onDismiss: () -> Unit,
    onSave: (HostProfile) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: ConnectionProtocol.SSH) }
    var port by remember { mutableStateOf((existing?.port ?: protocol.defaultPort).toString()) }
    var authentication by remember {
        mutableStateOf(existing?.authenticationMethod ?: AuthenticationMethod.PASSWORD)
    }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var privateKey by remember { mutableStateOf(existing?.privateKey.orEmpty()) }
    var privateKeyPassphrase by remember { mutableStateOf(existing?.privateKeyPassphrase.orEmpty()) }
    var keyMessage by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }

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

    fun saveProfile() {
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
                return
            }
        }

        keyError = null
        onSave(
            HostProfile(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                host = host.trim(),
                port = port.toInt(),
                username = username.trim(),
                protocol = protocol,
                authenticationMethod = authentication,
                password = if (authentication == AuthenticationMethod.PASSWORD) password else "",
                privateKey = normalizedKey,
                privateKeyPassphrase = if (authentication == AuthenticationMethod.PRIVATE_KEY) privateKeyPassphrase else "",
            ),
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
                                    if (pasted.isBlank()) {
                                        keyMessage = null
                                        keyError = "Schowek jest pusty."
                                    } else if (pasted.length > MAX_PRIVATE_KEY_CHARS) {
                                        keyMessage = null
                                        keyError = "Klucz w schowku jest zbyt duży."
                                    } else {
                                        privateKey = pasted.normalizePrivateKeyText()
                                        keyError = null
                                        keyMessage = "Klucz wklejony ze schowka."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Wklej") }

                            OutlinedButton(
                                onClick = {
                                    keyFileLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/octet-stream",
                                            "application/x-pem-file",
                                        ),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Wybierz plik") }
                        }

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
                            supportingText = { Text("Pełna zawartość razem z BEGIN i END.") },
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

                Text("Dane logowania w tej wersji pozostają tylko w pamięci aplikacji.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = formValid,
                onClick = ::saveProfile,
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
