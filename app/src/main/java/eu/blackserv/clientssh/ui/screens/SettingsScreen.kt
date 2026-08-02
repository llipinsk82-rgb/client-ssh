package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.BuildConfig
import eu.blackserv.clientssh.model.AppSettings
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.TerminalSettings
import eu.blackserv.clientssh.ui.theme.LocalPremiumSkin
import eu.blackserv.clientssh.ui.theme.PremiumActionButton
import eu.blackserv.clientssh.ui.theme.PremiumPanel
import eu.blackserv.clientssh.ui.theme.premiumTokensFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    terminalSettings: TerminalSettings,
    onAppSettingsChange: (AppSettings) -> Unit,
    onTerminalSettingsChange: (TerminalSettings) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val activeSkin = appSettings.skin.canonical
    val tokens = LocalPremiumSkin.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ustawienia", fontWeight = FontWeight.Bold)
                        Text(
                            "BlackServ Premium • v${BuildConfig.VERSION_NAME}",
                            color = tokens.accentBright,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.night.copy(alpha = 0.88f),
                    titleContentColor = tokens.text,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(
                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                title = "Motywy aplikacji",
                subtitle = "Każdy motyw zmienia cały interfejs, nie tylko kolor akcentu.",
            )

            AppSkin.selectableEntries.forEach { skin ->
                SkinOption(
                    skin = skin,
                    selected = activeSkin == skin,
                    onClick = { onAppSettingsChange(appSettings.copy(skin = skin)) },
                )
            }

            PremiumPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = tokens.accentBright)
                    Text(
                        "Splash screen zawsze pozostaje oficjalny Sapphire, niezależnie od wybranego motywu.",
                        color = tokens.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            SectionTitle(
                title = "Terminal i sesja",
                subtitle = "Ustawienia wspólne dla wszystkich profili.",
            )

            SettingsSwitchRow(
                title = "Działanie w tle / Session Keeper",
                subtitle = "Utrzymuje sesję po wyjściu z aplikacji i ponawia połączenie po zerwaniu.",
                checked = terminalSettings.backgroundSessionEnabled,
                onCheckedChange = {
                    onTerminalSettingsChange(terminalSettings.copy(backgroundSessionEnabled = it))
                },
            )

            SettingsSwitchRow(
                title = "Nie wygaszaj ekranu",
                subtitle = "Utrzymuje ekran aktywny podczas otwartej sesji terminala.",
                checked = terminalSettings.keepScreenAwake,
                onCheckedChange = {
                    onTerminalSettingsChange(terminalSettings.copy(keepScreenAwake = it))
                },
            )

            PlannedSetting(title = "Font terminala", value = "Wkrótce")
            PlannedSetting(title = "Język aplikacji", value = "Polski")
            PlannedSetting(title = "Eksport / import konfiguracji", value = "Wkrótce")

            Spacer(Modifier.height(2.dp))
            SectionTitle(
                icon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = null) },
                title = "Aktualizacje",
                subtitle = "Zainstalowana wersja: ${BuildConfig.VERSION_NAME}",
            )

            PremiumActionButton(
                text = "SPRAWDŹ AKTUALIZACJE",
                icon = Icons.Default.SystemUpdateAlt,
                onClick = onCheckUpdates,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    icon: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalPremiumSkin.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) { icon() }
        }
        Column {
            Text(title, color = tokens.text, fontWeight = FontWeight.Bold)
            Text(subtitle, color = tokens.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SkinOption(skin: AppSkin, selected: Boolean, onClick: () -> Unit) {
    val preview = premiumTokensFor(skin)
    PremiumPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        selected = selected,
        strong = true,
        cornerRadius = 18.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 92.dp, height = 62.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(preview.deep, preview.night)),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(preview.accent.copy(alpha = 0.58f), Color.Transparent),
                            center = Offset(size.width * 0.55f, size.height * 0.55f),
                            radius = size.width * 0.52f,
                        ),
                        radius = size.width * 0.52f,
                        center = Offset(size.width * 0.55f, size.height * 0.55f),
                    )
                    drawRoundRect(
                        color = preview.border,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                        style = Stroke(width = 2f),
                    )
                    val y = size.height * 0.70f
                    drawLine(preview.accentBright.copy(alpha = 0.80f), Offset(8f, y), Offset(size.width - 8f, y), 2f)
                    repeat(3) { index ->
                        drawCircle(
                            preview.accentBright,
                            radius = 2.4f,
                            center = Offset(size.width * (0.25f + index * 0.25f), y),
                        )
                    }
                }
                Text(
                    text = "BS",
                    color = preview.text,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(skin.label, color = preview.text, fontWeight = FontWeight.Bold)
                Text(
                    skin.description,
                    color = preview.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Box(
                modifier = Modifier.size(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = if (selected) preview.accent else preview.borderMuted,
                        style = Stroke(width = if (selected) 5f else 2f),
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Wybrany",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalPremiumSkin.current
    PremiumPanel(
        modifier = Modifier.fillMaxWidth(),
        strong = checked,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = tokens.text, fontWeight = FontWeight.Medium)
                Text(subtitle, color = tokens.muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = tokens.accent,
                    uncheckedThumbColor = tokens.muted,
                    uncheckedTrackColor = tokens.panelStrong,
                    uncheckedBorderColor = tokens.borderMuted,
                ),
            )
        }
    }
}

@Composable
private fun PlannedSetting(title: String, value: String) {
    val tokens = LocalPremiumSkin.current
    PremiumPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = tokens.text, modifier = Modifier.weight(1f))
            Text(value, color = tokens.accentBright, style = MaterialTheme.typography.labelMedium)
        }
    }
}
