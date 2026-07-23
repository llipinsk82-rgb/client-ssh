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
                        containerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = if (skin == AppSkin.NEON) 0.86f else 1f,
                        ),
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
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
            profile = profile,
            isActive = isActive,
            activeSessionStatus = activeSessionStatus,
            onConnect = onConnect,
            onDisconnectActiveSession = onDisconnectActiveSession,
            onOpenSftp = onOpenSftp,
            onClone = onClone,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    } else {
        ClassicProfileCard(
            profile = profile,
            isActive = isActive,
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
    val border = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, border),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        ClassicProfileContent(
            profile = profile,
            isActive = isActive,
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

@Composable
private fun ClassicProfileContent(
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
                Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
            }
        }

        if (isActive) {
            AssistChip(
                onClick = { onConnect(profile) },
                label = { Text("● AKTYWNA • $activeSessionStatus", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    labelColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onConnect(profile) }, modifier = Modifier.weight(1f)) {
                Text(if (isActive) "Wróć do terminala" else "Terminal")
            }
            if (profile.protocol == ConnectionProtocol.SSH) {
                OutlinedButton(onClick = { onOpenSftp(profile) }) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("SFTP")
                }
            }
        }

        if (isActive) {
            OutlinedButton(
                onClick = onDisconnectActiveSession,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Rozłącz sesję")
            }
        }
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
    val green = Color(0xFF58FF94)
    val cyan = Color(0xFF42DFFF)
    val amber = Color(0xFFFFCB4A)
    val red = Color(0xFFFF7187)
    val outline = if (isActive) green else Color(0xFF1B7246)
    val shape = RoundedCornerShape(22.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isActive) 12.dp else 7.dp,
                shape = shape,
                ambientColor = outline.copy(alpha = 0.34f),
                spotColor = outline.copy(alpha = 0.34f),
            ),
        shape = shape,
        border = BorderStroke(if (isActive) 1.3.dp else 1.dp, outline),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF07100D)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(62.dp)
                        .clip(RoundedCornerShape(50))
                        .background(green),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(green),
                )
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}",
                        color = Color(0xFFA9B7B2),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                NeonBadge(profile.protocol.label, green)
                Spacer(Modifier.width(6.dp))
                NeonIconButton(Icons.Default.ContentCopy, "Klonuj", Color(0xFFE5EFEB), green) { onClone(profile) }
                Spacer(Modifier.width(5.dp))
                NeonIconButton(Icons.Default.Edit, "Edytuj", cyan, cyan) { onEdit(profile) }
                Spacer(Modifier.width(5.dp))
                NeonIconButton(
                    icon = Icons.Default.Delete,
                    description = "Usuń",
                    tint = if (isActive) red.copy(alpha = 0.3f) else red,
                    border = if (isActive) red.copy(alpha = 0.25f) else red,
                    enabled = !isActive,
                ) { onDelete(profile) }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = green.copy(alpha = 0.22f))

            if (isActive) {
                AssistChip(
                    onClick = { onConnect(profile) },
                    label = {
                        Text(
                            "● AKTYWNA SESJA • $activeSessionStatus",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = green.copy(alpha = 0.10f),
                        labelColor = green,
                    ),
                    border = BorderStroke(1.dp, green.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NeonWideButton(
                    modifier = Modifier.weight(1f),
                    text = if (isActive) ">_  Wróć" else ">_  Terminal",
                    accent = green,
                    background = Color(0xFF08170F),
                    onClick = { onConnect(profile) },
                )

                if (profile.protocol == ConnectionProtocol.SSH) {
                    NeonWideButton(
                        modifier = Modifier.weight(0.92f),
                        text = "SFTP",
                        accent = amber,
                        background = Color(0xFF161107),
                        leadingIcon = Icons.Default.Folder,
                        onClick = { onOpenSftp(profile) },
                    )
                }
            }

            if (isActive) {
                OutlinedButton(
                    onClick = onDisconnectActiveSession,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, red.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF160B10),
                        contentColor = red,
                    ),
                    contentPadding = PaddingValues(vertical = 9.dp),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rozłącz aktywną sesję")
                }
            }
        }
    }
}

@Composable
private fun NeonBadge(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF091510),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.72f)),
    ) {
        Text(
            text = text,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun NeonIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    border: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, border.copy(alpha = if (enabled) 0.72f else 0.25f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF091311),
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = 0.3f),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun NeonWideButton(
    modifier: Modifier,
    text: String,
    accent: Color,
    background: Color,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = accent,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
