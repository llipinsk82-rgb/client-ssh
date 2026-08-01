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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.BuildConfig
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.ui.theme.AppBackdrop
import eu.blackserv.clientssh.ui.theme.LocalAppSkin
import eu.blackserv.clientssh.ui.theme.LocalPremiumSkin
import eu.blackserv.clientssh.ui.theme.PremiumActionButton
import eu.blackserv.clientssh.ui.theme.PremiumIconSurface
import eu.blackserv.clientssh.ui.theme.PremiumPanel

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
    @Suppress("UNUSED_VARIABLE")
    val ignoredDelete = onDelete
    val tokens = LocalPremiumSkin.current
    val skin = LocalAppSkin.current

    AppBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = tokens.night.copy(alpha = 0.88f),
                        titleContentColor = tokens.text,
                        actionIconContentColor = tokens.muted,
                    ),
                    title = {
                        Column {
                            Text("Client SSH", fontWeight = FontWeight.Bold)
                            Text(
                                "${skin.label} • v${BuildConfig.VERSION_NAME}",
                                color = tokens.accentBright,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onCheckUpdates) {
                            Icon(Icons.Default.SystemUpdateAlt, "Sprawdź aktualizacje")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAdd,
                    shape = CircleShape,
                    containerColor = tokens.accent,
                    contentColor = Color.White,
                ) { Icon(Icons.Default.Add, "Dodaj profil", modifier = Modifier.size(30.dp)) }
            },
        ) { padding ->
            if (profiles.isEmpty()) {
                EmptyProfiles(Modifier.padding(padding), onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val addressMarker = "${profile.host}:${profile.port}"
                        val activeFromSession = activeProfileId == profile.id
                        val activeFromConnectedStatus = activeSessionStatus.startsWith("SSH •") &&
                            activeSessionStatus.contains(addressMarker, ignoreCase = true)
                        ProfileCard(
                            profile = profile,
                            active = activeFromSession || activeFromConnectedStatus,
                            status = activeSessionStatus,
                            onEdit = onEdit,
                            onClone = onClone,
                            onConnect = onConnect,
                            onDisconnect = onDisconnectActiveSession,
                            onSftp = onOpenSftp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProfiles(modifier: Modifier, onAdd: () -> Unit) {
    val tokens = LocalPremiumSkin.current
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        PremiumPanel(
            modifier = Modifier.fillMaxWidth(),
            strong = true,
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Computer, null, tint = tokens.accentBright, modifier = Modifier.size(42.dp))
                Text("Brak profili", color = tokens.text, fontWeight = FontWeight.Bold)
                Text(
                    "Dodaj VPS, serwer lub tuner Enigma2 przez SSH albo Telnet.",
                    color = tokens.muted,
                )
                PremiumActionButton(
                    text = "DODAJ PIERWSZY PROFIL",
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: HostProfile,
    active: Boolean,
    status: String,
    onEdit: (HostProfile) -> Unit,
    onClone: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit,
    onDisconnect: () -> Unit,
    onSftp: (HostProfile) -> Unit,
) {
    val tokens = LocalPremiumSkin.current
    val address = "${profile.username.ifBlank { "—" }}@${profile.host}:${profile.port}"

    PremiumPanel(
        modifier = Modifier.fillMaxWidth(),
        strong = true,
        selected = active,
        cornerRadius = 19.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (active) tokens.success else tokens.accent,
                                tokens.accentBright,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            profile.name,
                            color = tokens.text,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            address,
                            color = tokens.muted,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusBadge(profile.protocol.label, active, status)
                    Spacer(Modifier.size(8.dp))
                    PremiumIconSurface(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edytuj",
                        onClick = { onEdit(profile) },
                        tint = tokens.accentBright,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (active) status else "GOTOWY DO POŁĄCZENIA",
                        modifier = Modifier.weight(1f),
                        color = if (active) tokens.success else tokens.muted,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onClone(profile) }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.FileCopy,
                            "Klonuj profil",
                            modifier = Modifier.size(18.dp),
                            tint = tokens.muted,
                        )
                    }
                }

                if (active) {
                    AssistChip(
                        onClick = { onConnect(profile) },
                        label = {
                            Text(
                                "AKTYWNA SESJA • DOTKNIJ, ABY WRÓCIĆ",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = tokens.success.copy(alpha = 0.10f),
                            labelColor = tokens.success,
                        ),
                        border = BorderStroke(1.dp, tokens.success.copy(alpha = 0.64f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PremiumActionButton(
                        text = if (active) ">_  WRÓĆ" else ">_  TERMINAL",
                        onClick = { onConnect(profile) },
                        modifier = Modifier.weight(1f),
                    )
                    if (profile.protocol == ConnectionProtocol.SSH) {
                        PremiumActionButton(
                            text = "SFTP",
                            icon = Icons.Default.Folder,
                            secondary = true,
                            onClick = { onSftp(profile) },
                            modifier = Modifier.weight(0.90f),
                        )
                    }
                }

                if (active) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, tokens.danger.copy(alpha = 0.86f)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = tokens.danger.copy(alpha = 0.08f),
                            contentColor = tokens.danger,
                        ),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(7.dp))
                        Text("ROZŁĄCZ AKTYWNĄ SESJĘ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, active: Boolean, status: String) {
    val tokens = LocalPremiumSkin.current
    val connecting = active && !status.startsWith("SSH •") &&
        listOf("łącz", "przywr", "ponow", "czeka").any { status.contains(it, true) }
    val color = when {
        connecting -> tokens.warning
        active -> tokens.success
        else -> tokens.muted
    }
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = tokens.panelStrong,
        border = BorderStroke(1.dp, color.copy(alpha = if (active) 0.90f else 0.52f)),
    ) {
        Text(
            text,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}
