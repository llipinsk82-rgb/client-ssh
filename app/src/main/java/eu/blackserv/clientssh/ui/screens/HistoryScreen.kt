package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.model.ConnectionHistoryEntry
import eu.blackserv.clientssh.model.ConnectionHistoryResult
import eu.blackserv.clientssh.storage.LocalAppStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val historyDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { LocalAppStore(context.applicationContext) }
    var entries by remember { mutableStateOf(store.loadConnectionHistory()) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Historia", fontWeight = FontWeight.Bold)
                        Text(
                            if (entries.isEmpty()) "Brak zapisanych sesji" else "${entries.size} ostatnich sesji",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = entries.isNotEmpty(),
                        onClick = { confirmClear = true },
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Wyczyść historię")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                ),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyHistory(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryCard(entry)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Wyczyścić historię?") },
            text = { Text("Usunięte zostaną wyłącznie wpisy historii. Profile i dane logowania pozostaną bez zmian.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.clearConnectionHistory()
                        entries = emptyList()
                        confirmClear = false
                    },
                ) { Text("Wyczyść", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Historia jest pusta", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(
            "Po pierwszym połączeniu zobaczysz tutaj host, czas rozpoczęcia, długość sesji i jej wynik.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HistoryCard(entry: ConnectionHistoryEntry) {
    val resultColor = when {
        entry.finishedAt == null -> MaterialTheme.colorScheme.tertiary
        entry.result == ConnectionHistoryResult.ERROR -> MaterialTheme.colorScheme.error
        entry.result == ConnectionHistoryResult.DISCONNECTED -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val resultLabel = when {
        entry.finishedAt == null -> "AKTYWNA"
        entry.result == ConnectionHistoryResult.ERROR -> "BŁĄD"
        entry.result == ConnectionHistoryResult.DISCONNECTED -> "ZAKOŃCZONA"
        else -> "POŁĄCZONO"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, resultColor.copy(alpha = .45f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.profileName.ifBlank { entry.host }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = resultColor.copy(alpha = .10f),
                    border = BorderStroke(1.dp, resultColor.copy(alpha = .65f)),
                ) {
                    Text(
                        resultLabel,
                        color = resultColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                "${entry.username}@${entry.host}:${entry.port}",
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Row {
                Text(formatTimestamp(entry.startedAt), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(10.dp))
                Text("• ${formatDuration(entry)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(entry.protocol.label, color = resultColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            }

            if (entry.message.isNotBlank()) {
                Text(entry.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(historyDateFormatter)

private fun formatDuration(entry: ConnectionHistoryEntry): String {
    val end = entry.finishedAt ?: System.currentTimeMillis()
    val totalSeconds = max(0L, (end - entry.startedAt) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "%d h %02d min".format(hours, minutes)
        minutes > 0 -> "%d min %02d s".format(minutes, seconds)
        else -> "$seconds s"
    }
}
