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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
                        containerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = if (skin == AppSkin.NEON) 0.84f else 1f,
                        ),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Column {
                            Text("Client SSH", fontWeight = FontWeight.Bold)
                            Text(
                                if (skin == AppSkin.NEON) {
                                    "BLACKSERV NEON // COMMAND DECK // v${BuildConfig.VERSION_NAME}"
                                } else {
                                    "BlackServ command deck • v${BuildConfig.VERSION_NAME}"
                                },
                                color = if (skin == AppSkin.NEON) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj profil")
                }
            },
        ) { padding ->
            if (profiles.isEmpty()) {
                EmptyProfiles(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    skin = skin,
                    onAdd = onAdd,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (skin == AppSkin.NEON) 14.dp else 8.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        if (skin == AppSkin.NEON) {
                            NeonHostCard(
                                profile = profile,
                                isActive = activeProfileId == profile.id,
                                activeSessionStatus = activeSessionStatus,
                                onConnect = onConnect,
                                onDisconnect = onDisconnectActiveSession,
                                onOpenSftp = onOpenSftp,
                                onClone = onClone,
                                onEdit = onEdit,
                                onDelete = onDelete,
                            )
                        } else {
                            ClassicHostCard(
                                profile = profile,
                                isActive = activeProfileId == profile.id,
                                activeSessionStatus = activeSessionStatus,
                                onConnect = onConnect,
                                onDisconnect = onDisconnectActiveSession,
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
}

@Composable
private fun EmptyProfiles(
    modifier: Modifier,
    skin: AppSkin,
    onAdd: () -> Unit,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = if (skin == AppSkin.NEON) 0.88f else 1f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = if (skin == AppSkin.NEON) 8.dp else 2.dp,
            shadowElevation = if (skin == AppSkin.NEON) 12.dp else 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Brak profili", fontWeight = FontWeight.Bold)
                Text(
                    "Dodaj VPS lub tuner Enigma2 przez SSH albo Telnet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onAdd, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Dodaj pierwszy profil")
                }
            }
        }
    }
}

@Composable
private fun ClassicHostCard(
    profile: HostProfile,
    isActive: Boolean,
    activeSessionStatus: String,
    onConnect: (HostProfile) -> Unit,
    onDisconnect: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AssistChip(onClick = {}, label = { Text(profile.protocol.label) })
                IconButton(onClick = { onClone(profile) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Klonuj")
                }
                IconButton(onClick = { onEdit(profile) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edytuj", tint = MaterialTheme.colorScheme.tertiary)
                }
                IconButton(enabled = !isActive, onClick = { onDelete(profile) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Usuń",
                        tint = if (isActive) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            ActiveSessionChip(isActive, activeSessionStatus) { onConnect(profile) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onConnect(profile) }, modifier = Modifier.weight(1f)) {
                    Text(if (isActive) "Wróć do terminala" else "Terminal")
                }
                if (profile.protocol == ConnectionProtocol.SSH) {
                    OutlinedButton(onClick = { onOpenSftp(profile) }) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(4.dp))
                        Text("SFTP")
                    }
                }
            }

            DisconnectButton(isActive, onDisconnect)
        }
    }
}

@Composable
private fun NeonHostCard(
    profile: HostProfile,
    isActive: Boolean,
    activeSessionStatus: String,
    onConnect: (HostProfile) -> Unit,
    onDisconnect: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
) {
    val green = Color(0xFF52FF8B)
    val cyan = Color(0xFF35DFFF)
    val amber = Color(0xFFFFC642)
    val red = Color(0xFFFF667D)
    val border = if (isActive) green else Color(0xFF235B43)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isActive) 18.dp else 10.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = border.copy(alpha = 0.55f),
                spotColor = border.copy(alpha = 0.55f),
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xF207100D)),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, border),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(50))
                        .background(green),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(green),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}",
                        color = Color(0xFFA9B8B1),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(profile.protocol.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF0B1712),
                        labelColor = green,
                    ),
                    border = BorderStroke(1.dp, green.copy(alpha = 0.65f)),
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = green.copy(alpha = 0.20f))
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonIconButton(Icons.Default.ContentCopy, "Klonuj", Color(0xFFDDE8E2), green) { onClone(profile) }
                NeonIconButton(Icons.Default.Edit, "Edytuj", cyan, cyan) { onEdit(profile) }
                NeonIconButton(
                    Icons.Default.Delete,
                    "Usuń",
                    red,
                    red,
                    enabled = !isActive,
                ) { onDelete(profile) }
            }

            ActiveSessionChip(isActive, activeSessionStatus) { onConnect(profile) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onConnect(profile) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.2.dp, green),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF08150E),
                        contentColor = green,
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                ) {
                    Text(if (isActive) ">_  Wróć" else ">_  Terminal", fontWeight = FontWeight.SemiBold)
                }

                if (profile.protocol == ConnectionProtocol.SSH) {
                    OutlinedButton(
                        onClick = { onOpenSftp(profile) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.2.dp, amber),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF171307),
                            contentColor = amber,
                        ),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = amber)
                        Spacer(Modifier.width(7.dp))
                        Text("SFTP", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            DisconnectButton(isActive, onDisconnect, neon = true)
        }
    }
}

@Composable
private fun NeonIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    borderColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = if (enabled) 0.85f else 0.22f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF0B1513),
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = 0.28f),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun ActiveSessionChip(
    isActive: Boolean,
    status: String,
    onClick: () -> Unit,
) {
    if (!isActive) return
    AssistChip(
        onClick = onClick,
        label = {
            Text("● AKTYWNA SESJA • $status", fontFamily = FontFamily.Monospace)
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            labelColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

@Composable
private fun DisconnectButton(
    isActive: Boolean,
    onDisconnect: () -> Unit,
    neon: Boolean = false,
) {
    if (!isActive) return
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        border = BorderStroke(1.dp, error.copy(alpha = 0.78f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (neon) Color(0xFF190B10) else Color.Transparent,
            contentColor = error,
        ),
        shape = if (neon) RoundedCornerShape(15.dp) else MaterialTheme.shapes.small,
    ) {
        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Rozłącz aktywną sesję")
    }
}
