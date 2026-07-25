package eu.blackserv.clientssh.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import eu.blackserv.clientssh.health.HealthCheckDiagnosticsRepository
import eu.blackserv.clientssh.health.HealthCheckExecutor
import eu.blackserv.clientssh.health.HealthCheckHistoryRepository
import eu.blackserv.clientssh.health.HealthCheckRecord
import eu.blackserv.clientssh.health.HealthCheckRepository
import eu.blackserv.clientssh.health.HealthCheckRunDiagnostic
import eu.blackserv.clientssh.health.HealthCheckRunOutcome
import eu.blackserv.clientssh.health.HealthCheckSnapshot
import eu.blackserv.clientssh.health.HealthMonitorConfig
import eu.blackserv.clientssh.health.HealthMonitorConfigRepository
import eu.blackserv.clientssh.health.HealthMonitorController
import eu.blackserv.clientssh.health.HealthMonitorScheduler
import eu.blackserv.clientssh.health.HealthStatus
import eu.blackserv.clientssh.health.HealthTarget
import eu.blackserv.clientssh.health.SharedPreferencesHealthCheckStorage
import eu.blackserv.clientssh.health.TcpHealthProbe
import eu.blackserv.clientssh.model.HostProfile
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMonitorScreen(profiles: List<HostProfile>) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val configRepository = remember(appContext) {
        HealthMonitorConfigRepository(
            SharedPreferencesHealthCheckStorage(
                context = appContext,
                valueKey = SharedPreferencesHealthCheckStorage.CONFIG_VALUE_KEY,
            ),
        )
    }
    val snapshotRepository = remember(appContext) {
        HealthCheckRepository(SharedPreferencesHealthCheckStorage(appContext))
    }
    val historyRepository = remember(appContext) {
        HealthCheckHistoryRepository(
            SharedPreferencesHealthCheckStorage(
                context = appContext,
                valueKey = SharedPreferencesHealthCheckStorage.HISTORY_VALUE_KEY,
            ),
        )
    }
    val diagnosticsRepository = remember(appContext) {
        HealthCheckDiagnosticsRepository(
            SharedPreferencesHealthCheckStorage(
                context = appContext,
                valueKey = SharedPreferencesHealthCheckStorage.DIAGNOSTICS_VALUE_KEY,
            ),
        )
    }
    val scheduler = remember(appContext) { HealthMonitorScheduler(appContext) }
    val controller = remember(configRepository, snapshotRepository, scheduler, historyRepository, diagnosticsRepository) {
        HealthMonitorController(
            configRepository = configRepository,
            snapshotRepository = snapshotRepository,
            scheduler = scheduler,
            historyRepository = historyRepository,
            diagnosticsRepository = diagnosticsRepository,
        )
    }
    val executor = remember(snapshotRepository, historyRepository) {
        HealthCheckExecutor(
            snapshotRepository = snapshotRepository,
            probe = TcpHealthProbe(),
            historyRepository = historyRepository,
        )
    }
    var configs by remember { mutableStateOf(configRepository.getAll().associateBy { it.profileId }) }
    var snapshots by remember { mutableStateOf(snapshotRepository.getAll().associateBy { it.profileId }) }
    var histories by remember(profiles) {
        mutableStateOf(profiles.associate { it.id to historyRepository.get(it.id) })
    }
    var diagnostics by remember(profiles) {
        mutableStateOf(diagnosticsRepository.getAll().associateBy { it.profileId })
    }
    var checkingProfileIds by remember { mutableStateOf(emptySet<String>()) }
    var testingWorkerProfileIds by remember { mutableStateOf(emptySet<String>()) }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    fun refresh() {
        configs = configRepository.getAll().associateBy { it.profileId }
        snapshots = snapshotRepository.getAll().associateBy { it.profileId }
        histories = profiles.associate { it.id to historyRepository.get(it.id) }
        diagnostics = diagnosticsRepository.getAll().associateBy { it.profileId }
    }

    fun save(config: HealthMonitorConfig) {
        controller.save(config)
        refresh()
    }

    fun checkNow(profile: HostProfile, config: HealthMonitorConfig) {
        if (profile.id in checkingProfileIds) return
        checkingProfileIds = checkingProfileIds + profile.id
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    executor.execute(
                        profileId = profile.id,
                        target = HealthTarget(
                            host = profile.host,
                            port = profile.port,
                            timeoutMs = config.timeoutMs,
                        ),
                        offlineFailureThreshold = config.offlineFailureThreshold,
                    )
                }
                refresh()
            } finally {
                checkingProfileIds = checkingProfileIds - profile.id
            }
        }
    }

    fun testWorkerNow(profileId: String) {
        if (profileId in testingWorkerProfileIds) return
        testingWorkerProfileIds = testingWorkerProfileIds + profileId
        val previousStartedAt = diagnosticsRepository.get(profileId)?.startedAt
        scope.launch {
            try {
                controller.testBackgroundWorkerNow(profileId)
                repeat(WORKER_TEST_REFRESH_ATTEMPTS) {
                    delay(WORKER_TEST_REFRESH_DELAY_MS)
                    refresh()
                    val current = diagnosticsRepository.get(profileId)
                    if (current != null && current.startedAt != previousStartedAt && current.outcome != HealthCheckRunOutcome.RUNNING) {
                        return@launch
                    }
                }
            } finally {
                refresh()
                testingWorkerProfileIds = testingWorkerProfileIds - profileId
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Health Monitor", fontWeight = FontWeight.Bold)
                        Text(
                            "TCP health check bez otwierania terminala",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                ),
            )
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Brak profili do monitorowania", fontWeight = FontWeight.Bold)
                Text(
                    "Dodaj profil serwera, aby włączyć cykliczny test TCP.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item(key = "notification-permission") {
                        NotificationPermissionCard(
                            onRequestPermission = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    val config = configs[profile.id] ?: HealthMonitorConfig(profileId = profile.id)
                    HealthProfileCard(
                        profile = profile,
                        config = config,
                        snapshot = snapshots[profile.id],
                        history = histories[profile.id].orEmpty(),
                        diagnostic = diagnostics[profile.id],
                        checking = profile.id in checkingProfileIds,
                        testingWorker = profile.id in testingWorkerProfileIds,
                        onSave = ::save,
                        onCheckNow = { checkNow(profile, config) },
                        onTestWorkerNow = { testWorkerNow(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Alerty są wyłączone", fontWeight = FontWeight.Bold)
            Text(
                "Pomiary w tle nadal działają. Zezwól na powiadomienia, aby otrzymywać alerty po zmianie ONLINE/OFFLINE.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text("Włącz powiadomienia")
            }
        }
    }
}

@Composable
private fun HealthProfileCard(
    profile: HostProfile,
    config: HealthMonitorConfig,
    snapshot: HealthCheckSnapshot?,
    history: List<HealthCheckRecord>,
    diagnostic: HealthCheckRunDiagnostic?,
    checking: Boolean,
    testingWorker: Boolean,
    onSave: (HealthMonitorConfig) -> Unit,
    onCheckNow: () -> Unit,
    onTestWorkerNow: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val statusColor = when (snapshot?.status ?: HealthStatus.UNKNOWN) {
        HealthStatus.ONLINE -> Color(0xFF3DDC84)
        HealthStatus.OFFLINE -> Color(0xFFFF7187)
        HealthStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (snapshot?.status ?: HealthStatus.UNKNOWN) {
        HealthStatus.ONLINE -> "ONLINE"
        HealthStatus.OFFLINE -> "OFFLINE"
        HealthStatus.UNKNOWN -> "NIEZNANY"
    }
    val now = System.currentTimeMillis()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = .55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(profile.name.ifBlank { profile.host }, fontWeight = FontWeight.Bold)
                    Text(
                        "${profile.host}:${profile.port}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onSave(config.copy(enabled = it)) },
                )
            }

            Text(
                snapshot?.let {
                    buildString {
                        append(it.message.ifBlank { "Oczekiwanie na wynik" })
                        it.responseTimeMs?.let { ms -> append(" • ${ms} ms") }
                        if (it.consecutiveFailures > 0) append(" • błędy: ${it.consecutiveFailures}")
                    }
                } ?: "Monitoring jeszcze nie wykonał pomiaru.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            snapshot?.lastCheckedAt?.let { checkedAt ->
                Text(
                    "Ostatni pomiar: ${healthTimestampLabel(checkedAt, now)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snapshot?.lastSuccessAt?.let { successAt ->
                Text(
                    "Ostatni sukces: ${healthTimestampLabel(successAt, now)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BackgroundRunCard(
                enabled = config.enabled,
                diagnostic = diagnostic,
                testing = testingWorker,
                now = now,
                onTestWorkerNow = onTestWorkerNow,
                onCopyReport = {
                    clipboard.setText(
                        AnnotatedString(
                            healthBackgroundReport(
                                profileId = profile.id,
                                enabled = config.enabled,
                                diagnostic = diagnostic,
                                snapshot = snapshot,
                                historySize = history.size,
                                now = now,
                            ),
                        ),
                    )
                },
            )

            if (history.isNotEmpty()) {
                Text("Ostatnie pomiary", style = MaterialTheme.typography.labelMedium)
                history.take(3).forEach { record ->
                    val recordColor = when (record.status) {
                        HealthStatus.ONLINE -> Color(0xFF3DDC84)
                        HealthStatus.OFFLINE -> Color(0xFFFF7187)
                        HealthStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            healthTimestampLabel(record.checkedAt, now),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            record.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = recordColor,
                            fontWeight = FontWeight.Bold,
                        )
                        record.responseTimeMs?.let { latency ->
                            Text(
                                " • ${latency} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onCheckNow,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checking) "Sprawdzanie…" else "Sprawdź teraz")
            }

            Text("Interwał", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 30L, 60L).forEach { minutes ->
                    FilterChip(
                        selected = config.intervalMinutes == minutes,
                        onClick = { onSave(config.copy(intervalMinutes = minutes)) },
                        label = { Text(if (minutes < 60) "$minutes min" else "1 h") },
                    )
                }
            }

            Text("Próg OFFLINE", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5).forEach { threshold ->
                    FilterChip(
                        selected = config.offlineFailureThreshold == threshold,
                        onClick = { onSave(config.copy(offlineFailureThreshold = threshold)) },
                        label = { Text("$threshold bł.") },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundRunCard(
    enabled: Boolean,
    diagnostic: HealthCheckRunDiagnostic?,
    testing: Boolean,
    now: Long,
    onTestWorkerNow: () -> Unit,
    onCopyReport: () -> Unit,
) {
    val label = when {
        !enabled -> "WYŁĄCZONY"
        testing && diagnostic?.outcome != HealthCheckRunOutcome.RUNNING -> "ZLECONY"
        diagnostic == null -> "OCZEKUJE"
        else -> diagnostic.outcome.name
    }
    val labelColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        diagnostic?.outcome == HealthCheckRunOutcome.SUCCESS -> Color(0xFF3DDC84)
        diagnostic?.outcome == HealthCheckRunOutcome.RETRY || diagnostic?.outcome == HealthCheckRunOutcome.FAILED ->
            Color(0xFFFF7187)
        diagnostic?.outcome == HealthCheckRunOutcome.RUNNING || testing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Worker w tle", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(label, color = labelColor, fontWeight = FontWeight.Bold)
            }
            when {
                !enabled -> Text("Okresowy pomiar jest wyłączony.", style = MaterialTheme.typography.bodySmall)
                testing && diagnostic?.outcome != HealthCheckRunOutcome.RUNNING -> Text(
                    "Test został zlecony. Oczekiwanie na uruchomienie przez Androida…",
                    style = MaterialTheme.typography.bodySmall,
                )
                diagnostic == null -> Text(
                    "Zadanie jest zaplanowane; Android wybierze dokładny czas wykonania.",
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> {
                    Text(
                        "Start: ${healthTimestampLabel(diagnostic.startedAt, now)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    diagnostic.finishedAt?.let {
                        Text(
                            "Koniec: ${healthTimestampLabel(it, now)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (diagnostic.detail.isNotBlank()) {
                        Text(diagnostic.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(
                onClick = onTestWorkerNow,
                enabled = enabled && !testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (testing) "Testowanie workera…" else "Testuj worker w tle")
            }
            OutlinedButton(onClick = onCopyReport, modifier = Modifier.fillMaxWidth()) {
                Text("Kopiuj raport testowy")
            }
        }
    }
}

internal fun healthBackgroundReport(
    profileId: String,
    enabled: Boolean,
    diagnostic: HealthCheckRunDiagnostic?,
    snapshot: HealthCheckSnapshot?,
    historySize: Int,
    now: Long = System.currentTimeMillis(),
): String = buildString {
    appendLine("Client SSH 0.3.5 — Health Monitor")
    appendLine("profile=${profileId.hashCode().toUInt().toString(16)}")
    appendLine("enabled=$enabled")
    appendLine("worker=${diagnostic?.outcome?.name ?: "NO_RUN"}")
    diagnostic?.let {
        appendLine("worker_started=${healthTimestampLabel(it.startedAt, now)}")
        appendLine("worker_finished=${it.finishedAt?.let { time -> healthTimestampLabel(time, now) } ?: "-"}")
        appendLine("worker_detail=${it.detail.ifBlank { "-" }}")
    }
    appendLine("status=${snapshot?.status?.name ?: HealthStatus.UNKNOWN.name}")
    appendLine("last_check=${snapshot?.lastCheckedAt?.let { healthTimestampLabel(it, now) } ?: "-"}")
    appendLine("last_success=${snapshot?.lastSuccessAt?.let { healthTimestampLabel(it, now) } ?: "-"}")
    appendLine("failures=${snapshot?.consecutiveFailures ?: 0}")
    append("history_records=$historySize")
}

internal fun healthTimestampLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - timestamp).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "przed chwilą"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} min temu"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L} godz. temu"
        else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    }
}

private const val WORKER_TEST_REFRESH_ATTEMPTS = 60
private const val WORKER_TEST_REFRESH_DELAY_MS = 500L
