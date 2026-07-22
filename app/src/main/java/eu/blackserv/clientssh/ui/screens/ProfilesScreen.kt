package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
private val ScreenVignette = Color(0xFF0B1511)
private val PanelColor = Color(0xFF101A16)
private val PanelDeep = Color(0xFF0B120F)
private val PanelStroke = Color(0xFF2F4339)
private val PanelHighlight = Color(0xFF526A5C)
private val AccentGreen = Color(0xFF62D58A)
private val AccentAmber = Color(0xFFD9A441)
private val MutedText = Color(0xFFA7B5AD)
private val DangerRed = Color(0xFFE88989)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profiles: List<HostProfile>,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBackground),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(ScreenVignette),
            )
            if (profiles.isEmpty()) {
                EmptyProfilesPanel(onAdd = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
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
}

@Composable
private fun EmptyProfilesPanel(onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = PanelColor,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 4.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, PanelStroke),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(34.dp))
                Text("Brak profili", fontWeight = FontWeight.Bold, color = Color(0xFFE6F0EA))
                Text("Dodaj VPS lub tuner Enigma2 przez SSH albo Telnet.", color = MutedText)
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color(0xFF06120B),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text("Dodaj pierwszy profil")
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelColor,
        contentColor = Color(0xFFE6F0EA),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        shadowElevation = 9.dp,
        border = BorderStroke(1.dp, PanelStroke),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(PanelHighlight.copy(alpha = 0.55f)))
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(48.dp)
                        .background(
                            if (profile.protocol == ConnectionProtocol.SSH) AccentGreen else AccentAmber,
                            RoundedCornerShape(8.dp),
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.name, fontWeight = FontWeight.Bold, color = Color(0xFFE6F0EA))
                        AssistChip(
                            onClick = {},
                            label = { Text(profile.protocol.label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = PanelDeep,
                                labelColor = if (profile.protocol == ConnectionProtocol.SSH) AccentGreen else AccentAmber,
                            ),
                            border = BorderStroke(1.dp, PanelStroke),
                        )
                    }
                    Text(
                        "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}",
                        color = MutedText,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp).background(PanelStroke.copy(alpha = 0.65f)))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onConnect(profile) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF0B1711),
                        contentColor = AccentGreen,
                    ),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Text("Terminal", fontWeight = FontWeight.SemiBold)
                }
                if (profile.protocol == ConnectionProtocol.SSH) {
                    OutlinedButton(
                        onClick = { onOpenSftp(profile) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PanelDeep,
                            contentColor = MutedText,
                        ),
                        border = BorderStroke(1.dp, PanelStroke),
                        shape = RoundedCornerShape(11.dp),
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
