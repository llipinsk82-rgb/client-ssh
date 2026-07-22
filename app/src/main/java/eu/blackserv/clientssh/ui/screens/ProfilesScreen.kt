package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.BuildConfig
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile

private val ScreenBackground = Color(0xFF050A08)
private val PanelColor = Color(0xFF0D1713)
private val PanelColorDeep = Color(0xFF08110E)
private val PanelStroke = Color(0xFF2C4437)
private val AccentGreen = Color(0xFF62D58A)
private val AccentAmber = Color(0xFFE0B35A)
private val MutedText = Color(0xFFA7B5AD)
private val DangerRed = Color(0xFFE88989)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profiles: List<HostProfile>,
    activeSessionName: String?,
    activeSessionStatus: String?,
    onResumeActiveSession: (() -> Unit)?,
    onDisconnectActiveSession: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF07100D),
                    titleContentColor = Color(0xFFE6F0EA),
                    actionIconContentColor = AccentGreen,
                ),
                title = {
                    Column {
                        Text("Client SSH", fontWeight = FontWeight.Bold)
                        Text(
                            "BlackServ command deck • v${BuildConfig.VERSION_NAME}",
                            color = MutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCheckUpdates) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Sprawdź aktualizacje")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = AccentGreen,
                contentColor = Color(0xFF06120B),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj profil")
            }
        },
    ) { padding ->
        if (profiles.isEmpty() && activeSessionName == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = PanelColor,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PanelStroke),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, tint = AccentGreen)
                        Text("Brak profili", fontWeight = FontWeight.Bold)
                        Text("Dodaj VPS lub tuner Enigma2 przez SSH albo Telnet.", color = MutedText)
                        Button(
                            onClick = onAdd,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color(0xFF06120B),
                            ),
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            Text("Dodaj pierwszy profil")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBackground),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeSessionName != null) {
                    item {
                        ActiveSessionCard(
                            name = activeSessionName,
                            status = activeSessionStatus.orEmpty(),
                            onResume = onResumeActiveSession,
                            onDisconnect = onDisconnectActiveSession,
                        )
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    HostProfileRow(
                        profile = profile,
                        onConnect = onConnect,
                        onOpenSftp = onOpenSftp,
                        onClone = onClone,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    name: String,
    status: String,
    onResume: (() -> Unit)?,
    onDisconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelColorDeep,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = AccentGreen)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sesja działa w tle", color = AccentGreen, fontWeight = FontWeight.Bold)
                    Text("$name • $status", color = MutedText, fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = onResume != null,
                    onClick = { onResume?.invoke() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color(0xFF06120B),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Wróć")
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = DangerRed)
                    Spacer(Modifier.width(4.dp))
                    Text("Rozłącz", color = DangerRed)
                }
            }
        }
    }
}

@Composable
private fun HostProfileRow(
    profile: HostProfile,
    onConnect: (HostProfile) -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, PanelStroke),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold, color = Color(0xFFE6F0EA))
                    Text(
                        "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}",
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AssistChip(onClick = {}, label = { Text(profile.protocol.label) })
                IconButton(onClick = { onClone(profile) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Klonuj", tint = MutedText)
                }
                IconButton(onClick = { onEdit(profile) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edytuj", tint = MutedText)
                }
                IconButton(onClick = { onDelete(profile) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = DangerRed)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onConnect(profile) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color(0xFF06120B),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Terminal")
                }
                if (profile.protocol == ConnectionProtocol.SSH) {
                    OutlinedButton(
                        onClick = { onOpenSftp(profile) },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = AccentAmber)
                        Spacer(Modifier.width(4.dp))
                        Text("SFTP")
                    }
                }
            }
        }
    }
}
