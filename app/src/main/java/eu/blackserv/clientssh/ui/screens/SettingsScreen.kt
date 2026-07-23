package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.BuildConfig
import eu.blackserv.clientssh.model.AppSettings
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.TerminalSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    terminalSettings: TerminalSettings,
    onAppSettingsChange: (AppSettings) -> Unit,
    onTerminalSettingsChange: (TerminalSettings) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ustawienia", fontWeight = FontWeight.Bold)
                        Text(
                            "Wygląd i zachowanie Client SSH",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
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
                title = "Skin aplikacji",
                subtitle = "Zmiana działa od razu i zostaje po restarcie.",
            )

            AppSkin.entries.forEach { skin ->
                SkinOption(
                    skin = skin,
                    selected = appSettings.skin == skin,
                    onClick = { onAppSettingsChange(appSettings.copy(skin = skin)) },
                )
            }

            Spacer(Modifier.height(2.dp))

            SectionTitle(
                title = "Terminal i sesja",
                subtitle = "Ustawienia wspólne dla wszystkich profili.",
            )

            SettingsSwitchRow(
                title = "Działanie w tle / Session Keeper",
                subtitle = "Utrzymuje sesję po wyjściu z aplikacji i ponawia połączenie po zerwaniu. Ctrl+D nadal kończy sesję normalnie.",
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

            OutlinedButton(
                onClick = onCheckUpdates,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                Text("Sprawdź aktualizacje", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.invoke()
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SkinOption(skin: AppSkin, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        tonalElevation = if (selected) 4.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(skin.label, fontWeight = FontWeight.Bold)
                Text(skin.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PlannedSetting(title: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}
