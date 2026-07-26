package eu.blackserv.clientssh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.blackserv.clientssh.health.HealthMonitorConfig
import eu.blackserv.clientssh.health.SshTelemetryFailureKind
import eu.blackserv.clientssh.health.SshTelemetryRecord
import eu.blackserv.clientssh.health.SshTelemetryRecordOutcome
import eu.blackserv.clientssh.health.SshTelemetrySample
import eu.blackserv.clientssh.health.TelemetryPingStatus
import eu.blackserv.clientssh.health.TelemetryPingTarget
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToLong

@Composable
internal fun SshTelemetryCard(
    profile: HostProfile,
    config: HealthMonitorConfig,
    record: SshTelemetryRecord?,
    onSave: (HealthMonitorConfig) -> Unit,
) {
    val supported = profile.protocol == ConnectionProtocol.SSH &&
        profile.authenticationMethod != AuthenticationMethod.INTERACTIVE
    var pingTargetInput by remember(config.profileId, config.pingTarget) {
        mutableStateOf(config.pingTarget)
    }
    val parsedPingTarget = TelemetryPingTarget.parse(pingTargetInput)
    val pingTargetValid = !config.pingEnabled || parsedPingTarget != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Telemetria SSH", fontWeight = FontWeight.Bold)
                    Text(
                        "CPU, RAM, load, dysk, sieć, uptime i ICMP ping",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.sshTelemetryEnabled,
                    enabled = supported,
                    onCheckedChange = { enabled ->
                        onSave(config.copy(sshTelemetryEnabled = enabled))
                    },
                )
            }

            if (!supported) {
                Text(
                    if (profile.protocol != ConnectionProtocol.SSH) {
                        "Telemetria zasobów wymaga profilu SSH."
                    } else {
                        "Profil interaktywny wymaga udziału użytkownika i nie może działać w tle."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            if (!config.sshTelemetryEnabled) {
                Text(
                    "Wyłączona. Dotychczasowy lekki test TCP działa niezależnie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            TelemetryResult(record)

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prawdziwy ICMP ping", fontWeight = FontWeight.Medium)
                    Text(
                        "Wykonywany z VPS do wskazanego celu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.pingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val target = parsedPingTarget ?: TelemetryPingTarget.DEFAULT
                            pingTargetInput = target.value
                            onSave(
                                config.copy(
                                    pingEnabled = true,
                                    pingTarget = target.value,
                                ),
                            )
                        } else {
                            onSave(config.copy(pingEnabled = false))
                        }
                    },
                )
            }

            if (config.pingEnabled) {
                OutlinedTextField(
                    value = pingTargetInput,
                    onValueChange = { value ->
                        if (value.length <= MAX_PING_TARGET_LENGTH) pingTargetInput = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !pingTargetValid,
                    label = { Text("Cel ping") },
                    supportingText = {
                        Text(
                            if (pingTargetValid) {
                                "IPv4 lub nazwa DNS, np. 1.1.1.1"
                            } else {
                                "Nieprawidłowy albo niebezpieczny cel ping."
                            },
                        )
                    },
                )
                OutlinedButton(
                    onClick = {
                        val target = parsedPingTarget ?: return@OutlinedButton
                        pingTargetInput = target.value
                        onSave(config.copy(pingTarget = target.value))
                    },
                    enabled = pingTargetValid && parsedPingTarget?.value != config.pingTarget,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Zapisz cel ping")
                }
            }
        }
    }
}

@Composable
private fun TelemetryResult(record: SshTelemetryRecord?) {
    when {
        record == null -> Text(
            "Brak próbki. Użyj „Sprawdź teraz” albo poczekaj na WorkManager.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        record.outcome == SshTelemetryRecordOutcome.FAILURE -> {
            val label = telemetryFailureLabel(record.failureKind)
            Text(label, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text(
                record.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TelemetryTimestamp(record.collectedAt)
        }

        record.sample != null -> {
            TelemetryMetrics(record.sample)
            TelemetryTimestamp(record.collectedAt)
        }
    }
}

@Composable
private fun TelemetryMetrics(sample: SshTelemetrySample) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetricRow("CPU", formatPercent(sample.cpuUsagePercent))
        MetricRow(
            "RAM",
            "${formatBytesFromKb(sample.memoryUsedKb)} / ${formatBytesFromKb(sample.memoryTotalKb)} " +
                "(${formatPercent(sample.memoryUsedPercent)})",
        )
        MetricRow(
            "LOAD",
            "${formatDecimal(sample.load1)} / ${formatDecimal(sample.load5)} / ${formatDecimal(sample.load15)}",
        )
        MetricRow(
            "DYSK /",
            "${sample.diskUsedPercent}% • ${formatBytesFromKb(sample.diskAvailableKb)} wolne",
        )
        MetricRow("UPTIME", formatUptime(sample.uptimeSeconds))
        MetricRow(
            "SIEĆ",
            "↓ ${formatRate(sample.networkRxBytesPerSecond)}  ↑ ${formatRate(sample.networkTxBytesPerSecond)}",
        )
        MetricRow("PING", formatPing(sample.pingStatus, sample.pingMs))
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TelemetryTimestamp(timestamp: Long) {
    Text(
        "Próbka: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun telemetryFailureLabel(kind: SshTelemetryFailureKind?): String = when (kind) {
    SshTelemetryFailureKind.HOST_KEY_NOT_TRUSTED -> "HOST KEY NIEZAAKCEPTOWANY"
    SshTelemetryFailureKind.AUTHENTICATION_FAILED -> "BŁĄD UWIERZYTELNIENIA"
    SshTelemetryFailureKind.CONNECT_TIMEOUT,
    SshTelemetryFailureKind.COMMAND_TIMEOUT,
    -> "TIMEOUT"

    SshTelemetryFailureKind.NETWORK_UNAVAILABLE -> "BRAK POŁĄCZENIA"
    SshTelemetryFailureKind.COMMAND_FAILED -> "POLECENIA NIEDOSTĘPNE"
    SshTelemetryFailureKind.RESPONSE_INVALID -> "BŁĘDNE DANE"
    SshTelemetryFailureKind.INTERACTIVE_AUTH_REQUIRED -> "WYMAGANE LOGOWANIE RĘCZNE"
    SshTelemetryFailureKind.UNSUPPORTED_PROFILE -> "NIEOBSŁUGIWANY PROFIL"
    SshTelemetryFailureKind.INTERNAL_ERROR,
    null,
    -> "TELEMETRIA NIEUDANA"
}

internal fun formatPercent(value: Double): String = "${formatDecimal(value)}%"

internal fun formatDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

internal fun formatBytesFromKb(kilobytes: Long): String = formatBytes(kilobytes * 1_024.0)

internal fun formatRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond.toDouble())}/s"

private fun formatBytes(bytes: Double): String {
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.coerceAtLeast(0.0)
    var unit = 0
    while (value >= 1_024.0 && unit < units.lastIndex) {
        value /= 1_024.0
        unit++
    }
    return if (unit == 0) "${value.roundToLong()} ${units[unit]}" else "${formatDecimal(value)} ${units[unit]}"
}

internal fun formatUptime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val days = safe / 86_400L
    val hours = (safe % 86_400L) / 3_600L
    val minutes = (safe % 3_600L) / 60L
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

internal fun formatPing(status: TelemetryPingStatus, pingMs: Double?): String = when (status) {
    TelemetryPingStatus.OK -> pingMs?.let { "${formatDecimal(it)} ms" } ?: "BŁĘDNE DANE"
    TelemetryPingStatus.DISABLED -> "WYŁĄCZONY"
    TelemetryPingStatus.UNAVAILABLE -> "BRAK NARZĘDZIA PING"
    TelemetryPingStatus.FAILED -> "BRAK ODPOWIEDZI"
}

private const val MAX_PING_TARGET_LENGTH = 253
