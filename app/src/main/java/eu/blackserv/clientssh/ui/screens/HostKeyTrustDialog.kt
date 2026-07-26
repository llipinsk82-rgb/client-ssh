package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.ssh.HostKeyTrustKind
import eu.blackserv.clientssh.ssh.HostKeyTrustRequest

@Composable
fun HostKeyTrustDialog(
    request: HostKeyTrustRequest,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val changed = request.kind == HostKeyTrustKind.CHANGED

    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                if (changed) "Klucz hosta SSH zmienił się" else "Zweryfikuj klucz hosta SSH",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (changed) {
                        "Połączenie zostało zablokowane. Nie akceptuj nowego klucza, dopóki nie potwierdzisz przyczyny zmiany niezależnym kanałem."
                    } else {
                        "To pierwsze połączenie z tym hostem. Porównaj fingerprint z wartością uzyskaną bezpośrednio z serwera lub panelu dostawcy."
                    },
                    color = if (changed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text("Host: ${request.host}:${request.port}")
                Text("Algorytm: ${request.algorithm}")
                Text("Fingerprint SHA-256:")
                SelectionContainer {
                    Text(
                        text = request.fingerprintSha256,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(request.fingerprintSha256))
                    },
                ) {
                    Text("Kopiuj fingerprint")
                }
            }
        },
        confirmButton = {
            if (changed) {
                TextButton(onClick = onReject) { Text("Zamknij") }
            } else {
                TextButton(onClick = onTrust) { Text("Zaufaj po weryfikacji") }
            }
        },
        dismissButton = if (changed) {
            null
        } else {
            { TextButton(onClick = onReject) { Text("Anuluj") } }
        },
    )
}
