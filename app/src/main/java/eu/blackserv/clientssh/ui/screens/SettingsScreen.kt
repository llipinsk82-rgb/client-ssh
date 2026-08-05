package eu.blackserv.clientssh.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.BuildConfig
import eu.blackserv.clientssh.backup.ConfigurationBackupCodec
import eu.blackserv.clientssh.backup.ConfigurationBackupException
import eu.blackserv.clientssh.backup.ConfigurationBackupSnapshot
import eu.blackserv.clientssh.backup.ConfigurationImportMode
import eu.blackserv.clientssh.backup.planConfigurationImport
import eu.blackserv.clientssh.health.HealthMonitorConfigRepository
import eu.blackserv.clientssh.health.HealthMonitorScheduler
import eu.blackserv.clientssh.health.SharedPreferencesHealthCheckStorage
import eu.blackserv.clientssh.model.AppSettings
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.TerminalSettings
import eu.blackserv.clientssh.storage.LocalAppStore
import eu.blackserv.clientssh.ui.theme.LocalPremiumSkin
import eu.blackserv.clientssh.ui.theme.PremiumActionButton
import eu.blackserv.clientssh.ui.theme.PremiumPanel
import eu.blackserv.clientssh.ui.theme.premiumTokensFor
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appStore = remember(appContext) { LocalAppStore(appContext) }
    val healthConfigRepository = remember(appContext) {
        HealthMonitorConfigRepository(
            SharedPreferencesHealthCheckStorage(
                context = appContext,
                valueKey = SharedPreferencesHealthCheckStorage.CONFIG_VALUE_KEY,
            ),
        )
    }
    val healthScheduler = remember(appContext) { HealthMonitorScheduler(appContext) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = pendingExportBytes
        try {
            if (uri != null && bytes != null) {
                context.contentResolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
                    ?: error("Nie można otworzyć pliku docelowego.")
                Toast.makeText(context, "Szyfrowana kopia została zapisana.", Toast.LENGTH_LONG).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(context, "Nie udało się zapisać kopii konfiguracji.", Toast.LENGTH_LONG).show()
        } finally {
            bytes?.fill(0)
            pendingExportBytes = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytesBounded(ConfigurationBackupCodec.MAX_FILE_BYTES)
            } ?: error("Nie można otworzyć pliku.")
            pendingImportBytes?.fill(0)
            pendingImportBytes = bytes
            showImportDialog = true
        } catch (_: Throwable) {
            Toast.makeText(
                context,
                "Nie udało się odczytać pliku albo plik jest zbyt duży.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun currentSnapshot(): ConfigurationBackupSnapshot = ConfigurationBackupSnapshot(
        profiles = appStore.loadProfiles(),
        favorites = appStore.loadFavorites(),
        appSettings = appStore.loadAppSettings(),
        terminalSettings = appStore.loadTerminalSettings(),
        healthMonitorConfigs = healthConfigRepository.getAll(),
    )

    fun exportConfiguration(password: CharArray) {
        try {
            val encrypted = ConfigurationBackupCodec.encrypt(currentSnapshot(), password)
            pendingExportBytes?.fill(0)
            pendingExportBytes = encrypted
            showExportDialog = false
            exportLauncher.launch(defaultBackupFilename())
        } catch (error: ConfigurationBackupException) {
            Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            Toast.makeText(context, "Nie udało się utworzyć kopii konfiguracji.", Toast.LENGTH_LONG).show()
        } finally {
            password.fill('\u0000')
        }
    }

    fun importConfiguration(password: CharArray, mode: ConfigurationImportMode) {
        val encrypted = pendingImportBytes
        if (encrypted == null) {
            password.fill('\u0000')
            showImportDialog = false
            return
        }
        try {
            val imported = ConfigurationBackupCodec.decrypt(encrypted, password)
            val plan = planConfigurationImport(currentSnapshot(), imported, mode)

            appStore.saveProfiles(plan.profiles)
            appStore.saveFavorites(plan.favorites)
            appStore.saveAppSettings(plan.appSettings)
            appStore.saveTerminalSettings(plan.terminalSettings)

            val plannedProfileIds = plan.profiles.mapTo(mutableSetOf()) { it.id }
            val plannedMonitorIds = plan.healthMonitorConfigs.mapTo(mutableSetOf()) { it.profileId }
            healthConfigRepository.getAll().forEach { existing ->
                if (existing.profileId !in plannedMonitorIds || existing.profileId !in plannedProfileIds) {
                    healthScheduler.cancel(existing.profileId)
                    healthConfigRepository.remove(existing.profileId)
                }
            }
            plan.healthMonitorConfigs
                .filter { it.profileId in plannedProfileIds }
                .forEach { config ->
                    healthConfigRepository.upsert(config)
                    healthScheduler.schedule(config)
                }

            showImportDialog = false
            Toast.makeText(
                context,
                "Zaimportowano ${plan.importedProfileCount} profili. Interfejs zostanie odświeżony.",
                Toast.LENGTH_LONG,
            ).show()
            context.findActivity()?.recreate()
        } catch (error: ConfigurationBackupException) {
            Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            Toast.makeText(context, "Nie udało się zaimportować konfiguracji.", Toast.LENGTH_LONG).show()
        } finally {
            password.fill('\u0000')
            encrypted.fill(0)
            pendingImportBytes = null
        }
    }

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

            Spacer(Modifier.height(2.dp))
            SectionTitle(
                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                title = "Szyfrowana kopia konfiguracji",
                subtitle = "Przenosi profile, hasła, klucze prywatne, polecenia, motyw i ustawienia Monitora.",
            )

            PremiumPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(13.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Plik jest chroniony hasłem i szyfrowany AES-256-GCM.",
                        color = tokens.text,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Fingerprinty hostów, historia sesji i logi terminala nie są eksportowane. Na nowym urządzeniu każdy serwer wymaga ponownej weryfikacji fingerprintu.",
                        color = tokens.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            PremiumActionButton(
                text = "UTWÓRZ SZYFROWANĄ KOPIĘ",
                icon = Icons.Default.Download,
                onClick = { showExportDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
            PremiumActionButton(
                text = "IMPORTUJ KOPIĘ",
                icon = Icons.Default.Upload,
                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            )

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

    if (showExportDialog) {
        ExportBackupDialog(
            onDismiss = { showExportDialog = false },
            onExport = ::exportConfiguration,
        )
    }

    if (showImportDialog && pendingImportBytes != null) {
        ImportBackupDialog(
            onDismiss = {
                showImportDialog = false
                pendingImportBytes?.fill(0)
                pendingImportBytes = null
            },
            onImport = ::importConfiguration,
        )
    }
}

@Composable
private fun ExportBackupDialog(
    onDismiss: () -> Unit,
    onExport: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length in ConfigurationBackupCodec.MIN_EXPORT_PASSWORD_LENGTH..
        ConfigurationBackupCodec.MAX_PASSWORD_LENGTH && password == confirmation

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Utwórz szyfrowaną kopię") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Ustaw osobne hasło do kopii. Bez tego hasła pliku nie będzie można odzyskać.",
                    style = MaterialTheme.typography.bodySmall,
                )
                PasswordField(
                    value = password,
                    onValueChange = { if (it.length <= ConfigurationBackupCodec.MAX_PASSWORD_LENGTH) password = it },
                    label = "Hasło kopii",
                )
                PasswordField(
                    value = confirmation,
                    onValueChange = { if (it.length <= ConfigurationBackupCodec.MAX_PASSWORD_LENGTH) confirmation = it },
                    label = "Powtórz hasło",
                    isError = confirmation.isNotEmpty() && password != confirmation,
                )
                Text(
                    "Minimum ${ConfigurationBackupCodec.MIN_EXPORT_PASSWORD_LENGTH} znaków.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val chars = password.toCharArray()
                    password = ""
                    confirmation = ""
                    onExport(chars)
                },
            ) { Text("ZAPISZ KOPIĘ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANULUJ") } },
    )
}

@Composable
private fun ImportBackupDialog(
    onDismiss: () -> Unit,
    onImport: (CharArray, ConfigurationImportMode) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ConfigurationImportMode.MERGE) }
    var replaceConfirmed by remember { mutableStateOf(false) }
    val canImport = password.isNotEmpty() &&
        password.length <= ConfigurationBackupCodec.MAX_PASSWORD_LENGTH &&
        (mode != ConfigurationImportMode.REPLACE || replaceConfirmed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importuj konfigurację") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PasswordField(
                    value = password,
                    onValueChange = { if (it.length <= ConfigurationBackupCodec.MAX_PASSWORD_LENGTH) password = it },
                    label = "Hasło kopii",
                )

                ImportModeRow(
                    selected = mode == ConfigurationImportMode.MERGE,
                    title = "Połącz z obecną konfiguracją",
                    subtitle = "Dopasowane profile zostaną zaktualizowane, a nowe dodane.",
                    onClick = {
                        mode = ConfigurationImportMode.MERGE
                        replaceConfirmed = false
                    },
                )
                ImportModeRow(
                    selected = mode == ConfigurationImportMode.REPLACE,
                    title = "Zastąp obecną konfigurację",
                    subtitle = "Profile i ustawienia, których nie ma w kopii, zostaną usunięte.",
                    onClick = { mode = ConfigurationImportMode.REPLACE },
                )

                if (mode == ConfigurationImportMode.REPLACE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceConfirmed = !replaceConfirmed },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = replaceConfirmed,
                            onCheckedChange = { replaceConfirmed = it },
                        )
                        Text(
                            "Rozumiem, że obecne profile zostaną zastąpione.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Text(
                    "Import nie przenosi zaufanych fingerprintów. Połączenia na nowym urządzeniu poproszą o ich ponowne sprawdzenie.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canImport,
                onClick = {
                    val chars = password.toCharArray()
                    password = ""
                    onImport(chars, mode)
                },
            ) {
                Text(if (mode == ConfigurationImportMode.REPLACE) "ZASTĄP" else "IMPORTUJ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANULUJ") } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun ImportModeRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
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

private fun java.io.InputStream.readBytesBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        total += read
        if (total > maxBytes) {
            buffer.fill(0)
            output.toByteArray().fill(0)
            error("Plik jest zbyt duży.")
        }
        output.write(buffer, 0, read)
    }
    buffer.fill(0)
    return output.toByteArray()
}

private fun defaultBackupFilename(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "client-ssh-backup-$timestamp.${ConfigurationBackupCodec.FILE_EXTENSION}"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
