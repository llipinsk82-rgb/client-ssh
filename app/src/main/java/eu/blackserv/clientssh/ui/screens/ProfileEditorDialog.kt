package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import java.util.UUID

@Composable
fun ProfileEditorDialog(
    existing: HostProfile? = null,
    onDismiss: () -> Unit,
    onSave: (HostProfile) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: ConnectionProtocol.SSH) }
    var port by remember { mutableStateOf((existing?.port ?: protocol.defaultPort).toString()) }
    var authentication by remember {
        mutableStateOf(existing?.authenticationMethod ?: AuthenticationMethod.PASSWORD)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nowy profil" else "Edytuj profil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.weight(.65f), label = { Text("Port") }, singleLine = true)
                }
                Text("Logowanie")
                AuthenticationMethod.entries
                    .filter { protocol == ConnectionProtocol.SSH || it != AuthenticationMethod.PRIVATE_KEY }
                    .forEach { option ->
                        FilterChip(
                            selected = authentication == option,
                            onClick = { authentication = option },
                            label = { Text(option.label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                Text("Hasła i klucze będą przechowywane osobno w Android Keystore; profil nie przechowuje sekretów.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && host.isNotBlank() && port.toIntOrNull()?.let { it in 1..65535 } == true,
                onClick = {
                    onSave(
                        HostProfile(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            host = host.trim(),
                            port = port.toInt(),
                            username = username.trim(),
                            protocol = protocol,
                            authenticationMethod = authentication,
                        ),
                    )
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
