package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.BuildConfig
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.ui.theme.AppBackdrop
import eu.blackserv.clientssh.ui.theme.LocalAppSkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profiles: List<HostProfile>,
    activeProfileId: String?,
    activeSessionStatus: String,
    onAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit,
    onDisconnectActiveSession: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val skin = LocalAppSkin.current

    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (skin == AppSkin.NEON) 0.86f else 1f),
                    ),
                    title = {
                        Column {
                            Text("Client SSH", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (skin == AppSkin.NEON) {
                                    "BLACKSERV NEON // v${BuildConfig.VERSION_NAME}"
                                } else {
                                    "BlackServ Classic • v${BuildConfig.VERSION_NAME}"
                                },
                                color = if (skin == AppSkin.NEON) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj profil")
                }
            },
        ) { padding ->
            if (profiles.isEmpty()) {
                EmptyProfiles(modifier = Modifier.padding(padding), onAdd = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        HostProfileCard(
                            profile = profile,
                            isActive = activeProfileId == profile.id,
                            activeSessionStatus = activeSessionStatus,
                            onConnect = onConnect,
                            onDisconnectActiveSession = onDisconnectActiveSession,
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
private fun EmptyProfiles(modifier: Modifier, onAdd: () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Brak profili", fontWeight = FontWeight.Bold)
                Text("Dodaj VPS lub tuner Enigma2 przez SSH albo Telnet.")
                Button(onClick = onAdd) { Text("Dodaj pierwszy profil") }
            }
        }
    }
}

@Composable
private fun HostProfileCard(
    profile: HostProfile,
    isActive: Boolean,
    activeSessionStatus: String,
    onConnect: (HostProfile) -> Unit,
    onDisconnectActiveSession: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
) {
    if (LocalAppSkin.current == AppSkin.NEON) {
        NeonProfileCard(
            profile, isActive, activeSessionStatus,
            onConnect, onDisconnectActiveSession, onOpenSftp, onClone, onEdit, onDelete,
        )
    } else {
        ClassicProfileCard(
            profile, isActive, activeSessionStatus,
            onConnect, onDisconnectActiveSession, onOpenSftp, onClone, onEdit, onDelete,
        )
    }
}

@Composable
private fun ClassicProfileCard(
    profile: HostProfile,
    isActive: Boolean,
    activeSessionStatus: String,
    onConnect: (HostProfile) -> Unit,
    onDisconnectActiveSession: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
) {
    ProfileCardFrame(
        border = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        background = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(13.dp),
        shadow = 0.dp,
    ) {
        ProfileContent(
            profile, isActive, activeSessionStatus,
            onConnect, onDisconnectActiveSession, onOpenSftp, onClone, onEdit, onDelete,
            neon = false,
        )
    }
}

@Composable
private fun NeonProfileCard(
    profile: HostProfile,
    isActive: Boolean,
    activeSessionStatus: String,
    onConnect: (HostProfile) -> Unit,
    onDisconnectActiveSession: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
) {
    val glow = if (isActive) Color(0xFF53FF8F) else Color(0xFF1C7546)
    ProfileCardFrame(
        border = glow,
        background = Color(0xFF07100D),
        shape = RoundedCornerShape(16.dp),
        shadow = if (isActive) 9.dp else 4.dp,
    ) {
        ProfileContent(
            profile, isActive, activeSessionStatus,
            onConnect, onDisconnectActiveSession, onOpenSftp, onClone, onEdit, onDelete,
            neon = true,
        )
    }
}

@Composable
private fun ProfileCardFrame(
    border: Color,
    background: Color,
    shape: RoundedCornerShape,
    shadow: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(shadow, shape, ambientColor = border.copy(alpha = 0.35f), spotColor = border.copy(alpha = 0.35f)),
        shape = shape,
        border = BorderStroke(1.dp, border),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(0.dp),
    ) { content() }
}

@Composable
private fun ProfileContent(
    profile: HostProfile,
    isActive: Boolean,
    activeSessionStatus: String,
    onConnect: (HostProfile) -> Unit,
    onDisconnectActiveSession: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    neon: Boolean,
) {
    val green = if (neon) Color(0xFF57FF92) else MaterialTheme.colorScheme.primary
    val cyan = if (neon) Color(0xFF37D8FF) else MaterialTheme.colorScheme.tertiary
    val amber = if (neon) Color(0xFFFFCA46) else MaterialTheme.colorScheme.secondary
    val red = if (neon) Color(0xFFFF7187) else MaterialTheme.colorScheme.error

    Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (neon) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Surface(modifier = Modifier.fillMaxSize(), color = green, shape = CircleShape) {}
                }
                Spacer(Modifier.width(7.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AssistChip(
                onClick = {},
                label = { Text(profile.protocol.label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (neon) Color(0xFF0A1612) else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = green,
                ),
                border = BorderStroke(1.dp, green.copy(alpha = 0.7f)),
            )

            CompactIconButton(Icons.Default.ContentCopy, "Klonuj", MaterialTheme.colorScheme.onSurfaceVariant) { onClone(profile) }
            CompactIconButton(Icons.Default.Edit, "Edytuj", cyan) { onEdit(profile) }
            CompactIconButton(Icons.Default.Delete, "Usuń", if (isActive) red.copy(alpha = 0.25f) else red, enabled = !isActive) { onDelete(profile) }
        }

        if (isActive) {
            AssistChip(
                onClick = { onConnect(profile) },
                label = {
                    Text("● AKTYWNA • $activeSessionStatus", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = green.copy(alpha = 0.10f),
                    labelColor = green,
                ),
                border = BorderStroke(1.dp, green.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Button(
                onClick = { onConnect(profile) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (neon) Color(0xFF08170F) else MaterialTheme.colorScheme.primary,
                    contentColor = if (neon) green else MaterialTheme.colorScheme.onPrimary,
                ),
                border = if (neon) BorderStroke(1.dp, green) else null,
                contentPadding = PaddingValues(vertical = 9.dp, horizontal = 10.dp),
            ) {
                Text(if (isActive) ">_ Wróć" else ">_ Terminal", fontWeight = FontWeight.SemiBold)
            }

            if (profile.protocol == ConnectionProtocol.SSH) {
                OutlinedButton(
                    onClick = { onOpenSftp(profile) },
                    modifier = Modifier.weight(0.82f),
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, amber),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (neon) Color(0xFF151106) else Color.Transparent,
                        contentColor = amber,
                    ),
                    contentPadding = PaddingValues(vertical = 9.dp, horizontal = 10.dp),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("SFTP")
                }
            }
        }

        if (isActive) {
            OutlinedButton(
                onClick = onDisconnectActiveSession,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, red.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = red),
                contentPadding = PaddingValues(vertical = 7.dp),
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Rozłącz sesję")
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}
