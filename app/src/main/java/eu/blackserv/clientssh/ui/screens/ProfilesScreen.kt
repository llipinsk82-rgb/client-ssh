package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    profiles: List<HostProfile>, activeProfileId: String?, activeSessionStatus: String,
    onAdd: () -> Unit, onEdit: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit, onDelete: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit, onDisconnectActiveSession: () -> Unit,
    onOpenSftp: (HostProfile) -> Unit, onCheckUpdates: () -> Unit,
) {
    val neon = LocalAppSkin.current == AppSkin.NEON
    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (neon) .86f else 1f),
                    ),
                    title = {
                        Column {
                            Text("Client SSH", fontWeight = FontWeight.Bold)
                            Text(
                                if (neon) "BLACKSERV NEON // v${BuildConfig.VERSION_NAME}" else "BlackServ Classic • v${BuildConfig.VERSION_NAME}",
                                color = if (neon) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    actions = { IconButton(onClick = onCheckUpdates) { Icon(Icons.Default.SystemUpdateAlt, "Sprawdź aktualizacje") } },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = if (neon) Color(0xFF0B1712) else MaterialTheme.colorScheme.primary,
                    contentColor = if (neon) NeonGreen else MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Default.Add, "Dodaj profil") }
            },
        ) { padding ->
            if (profiles.isEmpty()) EmptyProfiles(Modifier.padding(padding), onAdd)
            else LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        active = activeProfileId == profile.id,
                        status = activeSessionStatus,
                        neon = neon,
                        onEdit = onEdit,
                        onConnect = onConnect,
                        onDisconnect = onDisconnectActiveSession,
                        onSftp = onOpenSftp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProfiles(modifier: Modifier, onAdd: () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary)
                Text("Brak profili", fontWeight = FontWeight.Bold)
                Text("Dodaj VPS lub tuner Enigma2 przez SSH albo Telnet.")
                Button(onClick = onAdd) { Text("Dodaj pierwszy profil") }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: HostProfile, active: Boolean, status: String, neon: Boolean,
    onEdit: (HostProfile) -> Unit, onConnect: (HostProfile) -> Unit,
    onDisconnect: () -> Unit, onSftp: (HostProfile) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val address = "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}"
    val accent = if (neon) NeonGreen else MaterialTheme.colorScheme.primary
    val outline = if (active) accent else if (neon) Color(0xFF1B7246) else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(if (neon) 20.dp else 13.dp)
    val cardModifier = Modifier.fillMaxWidth().then(
        if (neon) Modifier.shadow(if (active) 10.dp else 5.dp, shape, outline.copy(.30f), outline.copy(.30f)) else Modifier,
    )

    Card(
        modifier = cardModifier,
        shape = shape,
        border = BorderStroke(if (active) 1.2.dp else 1.dp, outline),
        colors = CardDefaults.cardColors(containerColor = if (neon) Color(0xFF07100D) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            if (neon) Box(Modifier.width(3.dp).height(if (active) 218.dp else 158.dp).background(Color(0xFF1E7A4A)))
            Column(Modifier.weight(1f).padding(horizontal = if (neon) 14.dp else 12.dp, vertical = if (neon) 12.dp else 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        modifier = Modifier.weight(1f),
                        color = if (neon) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusBadge(profile.protocol.label, active, status, neon)
                    Spacer(Modifier.width(6.dp))
                    EditButton(neon) { onEdit(profile) }
                }

                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        address,
                        modifier = Modifier.weight(1f),
                        color = if (neon) Color(0xFFADBAB6) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(address)) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ContentCopy, "Kopiuj adres", modifier = Modifier.size(18.dp), tint = if (neon) Color(0xFF9CB2AA) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (neon) { Spacer(Modifier.height(8.dp)); HorizontalDivider(color = NeonGreen.copy(.22f)) }

                if (active) AssistChip(
                    onClick = { onConnect(profile) },
                    label = { Text("AKTYWNA SESJA • $status", maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = accent.copy(.10f), labelColor = accent),
                    border = BorderStroke(1.dp, accent.copy(.55f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                )

                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ActionButton(
                        Modifier.weight(1f), if (active) ">_  Wróć" else ">_  Terminal", accent,
                        if (neon) Color(0xFF08170F) else MaterialTheme.colorScheme.primary,
                        if (neon) accent else MaterialTheme.colorScheme.onPrimary,
                    ) { onConnect(profile) }
                    if (profile.protocol == ConnectionProtocol.SSH) ActionButton(
                        Modifier.weight(.9f), "SFTP", if (neon) NeonAmber else MaterialTheme.colorScheme.secondary,
                        if (neon) Color(0xFF161107) else Color.Transparent,
                        if (neon) NeonAmber else MaterialTheme.colorScheme.secondary,
                        Icons.Default.Folder,
                    ) { onSftp(profile) }
                }

                if (active) OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                    shape = RoundedCornerShape(if (neon) 14.dp else 10.dp),
                    border = BorderStroke(1.dp, NeonRed.copy(.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (neon) Color(0xFF160B10) else Color.Transparent, contentColor = NeonRed),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rozłącz aktywną sesję")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, active: Boolean, status: String, neon: Boolean) {
    val connecting = active && listOf("łącz", "przywr", "ponow", "czeka").any { status.contains(it, true) }
    val color = when { connecting -> NeonAmber; active -> NeonGreen; neon -> Color(0xFF73827C); else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (neon) Color(0xFF091510) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, color.copy(if (active) .85f else .5f)),
    ) { Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) }
}

@Composable
private fun EditButton(neon: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (neon) NeonCyan.copy(.8f) else MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (neon) Color(0xFF091311) else Color.Transparent, contentColor = if (neon) NeonCyan else MaterialTheme.colorScheme.tertiary),
        contentPadding = PaddingValues(0.dp),
    ) { Icon(Icons.Default.Edit, "Edytuj", modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ActionButton(
    modifier: Modifier, text: String, border: Color, background: Color, content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = background, contentColor = content),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 10.dp),
    ) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

private val NeonGreen = Color(0xFF58FF94)
private val NeonCyan = Color(0xFF42DFFF)
private val NeonAmber = Color(0xFFFFCB4A)
private val NeonRed = Color(0xFFFF7187)
