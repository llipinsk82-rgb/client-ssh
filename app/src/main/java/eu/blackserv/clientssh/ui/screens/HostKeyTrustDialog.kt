package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.blackserv.clientssh.ssh.HostKeyTrustKind
import eu.blackserv.clientssh.ssh.HostKeyTrustRequest
import eu.blackserv.clientssh.ui.theme.LocalPremiumSkin
import eu.blackserv.clientssh.ui.theme.PremiumActionButton
import eu.blackserv.clientssh.ui.theme.PremiumPanel

internal fun canAcceptHostKey(kind: HostKeyTrustKind): Boolean = kind == HostKeyTrustKind.UNKNOWN

@Composable
fun HostKeyTrustDialog(
    request: HostKeyTrustRequest,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val tokens = LocalPremiumSkin.current
    val changed = !canAcceptHostKey(request.kind)
    val stateColor = if (changed) tokens.danger else tokens.accentBright

    Dialog(
        onDismissRequest = onReject,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        PremiumPanel(
            modifier = Modifier
                .fillMaxWidth(0.91f)
                .padding(vertical = 18.dp),
            strong = true,
            selected = !changed,
            cornerRadius = 22.dp,
            contentPadding = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(stateColor.copy(alpha = 0.16f), Color.Transparent),
                            ),
                        )
                        .padding(top = 18.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(tokens.panelStrong)
                            .border(1.4.dp, stateColor.copy(alpha = 0.90f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = stateColor,
                            modifier = Modifier.size(52.dp),
                        )
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (changed) {
                            "Klucz hosta SSH zmienił się"
                        } else {
                            "Zweryfikuj klucz hosta SSH"
                        },
                        color = tokens.text,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = if (changed) {
                            "Połączenie zostało zablokowane. Serwer pokazuje inny klucz niż wcześniej. Potwierdź zmianę w panelu serwera albo bezpośrednio na serwerze — nie akceptuj jej w ciemno."
                        } else {
                            "To pierwsze połączenie z tym hostem. Porównaj fingerprint z wartością wyświetloną w panelu dostawcy lub bezpośrednio na serwerze."
                        },
                        color = if (changed) tokens.danger else tokens.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    PremiumPanel(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(13.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecurityValue("HOST", "${request.host}:${request.port}")
                            SecurityValue("ALGORYTM", request.algorithm)
                            Text(
                                "FINGERPRINT SHA-256",
                                color = tokens.muted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            SelectionContainer {
                                Text(
                                    text = request.fingerprintSha256,
                                    color = tokens.text,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Text(
                        "Fingerprint to odcisk publicznego klucza serwera — nie jest hasłem ani kluczem prywatnym.",
                        color = tokens.muted,
                        style = MaterialTheme.typography.labelSmall,
                    )

                    PremiumActionButton(
                        text = "KOPIUJ FINGERPRINT",
                        icon = Icons.Default.ContentCopy,
                        onClick = { clipboard.setText(AnnotatedString(request.fingerprintSha256)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!changed) {
                        PremiumActionButton(
                            text = "ZAUFAJ PO WERYFIKACJI",
                            icon = Icons.Default.Shield,
                            onClick = onTrust,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    PremiumActionButton(
                        text = if (changed) "ZAMKNIJ" else "ANULUJ",
                        onClick = onReject,
                        modifier = Modifier.fillMaxWidth(),
                        secondary = changed,
                    )
                    Spacer(Modifier.size(2.dp))
                }
            }
        }
    }
}

@Composable
private fun SecurityValue(label: String, value: String) {
    val tokens = LocalPremiumSkin.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = tokens.muted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(86.dp),
        )
        Text(
            value,
            color = tokens.text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
