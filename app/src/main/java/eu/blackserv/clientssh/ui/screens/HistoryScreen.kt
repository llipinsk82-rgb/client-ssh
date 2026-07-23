package eu.blackserv.clientssh.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
import eu.blackserv.clientssh.MainActivity
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.ConnectionHistoryEntry
import eu.blackserv.clientssh.model.ConnectionHistoryResult
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.service.TerminalSessionService
import eu.blackserv.clientssh.storage.LocalAppStore
import eu.blackserv.clientssh.terminal.PendingSessionRegistry
import eu.blackserv.clientssh.terminal.TerminalSessionBus
import eu.blackserv.clientssh.ui.theme.LocalAppSkin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val historyDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm:ss")
private val neonPrimaryText = Color(0xFFF2F7F4)
private val neonSecondaryText = Color(0xFFAFBDB7)
private val neonCard = Color(0xFF07100D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val neon = LocalAppSkin.current == AppSkin.NEON
    val primaryText = if (neon) neonPrimaryText else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (neon) neonSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant
    val store = remember(context.applicationContext) { LocalAppStore(context.applicationContext) }
    var entries by remember { mutableStateOf(store.loadConnectionHistory()) }
    var confirmClear by remember { mutableStateOf(false) }
    val profilesById = remember(entries) { store.loadProfiles().associateBy { it.id } }

    fun reconnect(profile: HostProfile) {
        PendingSessionRegistry.put(profile)
        TerminalSessionBus.begin(profile)
        ContextCompat.startForegroundService(
            context,
            Intent(context, TerminalSessionService::class.java)
                .putExtra(TerminalSessionService.EXTRA_PROFILE_ID, profile.id),
        )
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_ACTIVE_TERMINAL)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Historia", color = primaryText, fontWeight = FontWeight.Bold)
                        Text(
                            if (entries.isEmpty()) "Brak zapisanych sesji" else "${entries.size} ostatnich sesji",
                            color = secondaryText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(enabled = entries.isNotEmpty(), onClick = { confirmClear = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Wyczyść historię",
                            tint = if (entries.isNotEmpty()) secondaryText else secondaryText.copy(alpha = .35f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                    titleContentColor = primaryText,
                ),
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyHistory(
                modifier = Modifier.fillMaxSize().padding(padding),
                primaryText = primaryText,
                secondaryText = secondaryText,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    val profile = profilesById[entry.profileId]
                    HistoryCard(
                        entry = entry,
                        neon = neon,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        profileAvailable = profile != null,
                        onReconnect = profile?.let { { reconnect(it) } },
                    )
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
private fun EmptyHistory(
    modifier: Modifier,
    primaryText: Color,
    secondaryText: Color,
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF58FF94))
        Text(
            "Historia jest pusta",
            color = primaryText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "Po pierwszym połączeniu zobaczysz tutaj host, czas rozpoczęcia, długość sesji i jej wynik.",
            color = secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HistoryCard(
    entry: ConnectionHistoryEntry,
    neon: Boolean,
    primaryText: Color,
    secondaryText: Color,
    profileAvailable: Boolean,
    onReconnect: (() -> Unit)?,
) {
    val resultColor = when {
        entry.finishedAt == null -> Color(0xFF58FF94)
        entry.result == ConnectionHistoryResult.ERROR -> Color(0xFFFF7187)
        entry.result == ConnectionHistoryResult.DISCONNECTED -> Color(0xFFFFCB4A)
        else -> Color(0xFF58FF94)
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
        border = BorderStroke(1.dp, resultColor.copy(alpha = .55f)),
        colors = CardDefaults.cardColors(
            containerColor = if (neon) neonCard else MaterialTheme.colorScheme.surface,
            contentColor = primaryText,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.profileName.ifBlank { entry.host },
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = resultColor.copy(alpha = .10f),
                    border = BorderStroke(1.dp, resultColor.copy(alpha = .70f)),
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
                color = secondaryText,
                style = MaterialTheme.typography.bodySmall,
            )

            Row {
                Text(formatTimestamp(entry.startedAt), color = primaryText, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(10.dp))
                Text("• ${formatDuration(entry)}", color = secondaryText, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(entry.protocol.label, color = resultColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            }

            if (entry.message.isNotBlank()) {
                Text(entry.message, color = secondaryText, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedButton(
                onClick = { onReconnect?.invoke() },
                enabled = profileAvailable && onReconnect != null,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                border = BorderStroke(
                    1.dp,
                    if (profileAvailable) Color(0xFF42DFFF).copy(alpha = .75f) else secondaryText.copy(alpha = .30f),
                ),
            ) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (profileAvailable) "Połącz ponownie" else "Profil został usunięty")
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
