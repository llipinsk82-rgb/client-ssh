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

private val MonitorPanel = Color(0xFF111B16)
private val MonitorTile = Color(0xFF18241D)
private val MonitorPrimaryText = Color(0xFFE7F4EB)
private val MonitorSecondaryText = Color(0xFFA9B8AF)

@Composable
internal fun SshTelemetrySummary(
    record: SshTelemetryRecord?,
    tcpLatencyMs: Long?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            record == null -> Text(
                "Naciśnij „Sprawdź teraz”, aby pobrać stan serwera.",
                style = MaterialTheme.typography.labelMedium,
                color = MonitorSecondaryText,
            )

            record.outcome == SshTelemetryRecordOutcome.FAILURE -> {
                Text(
                    telemetryFailureLabel(record.failureKind),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    record.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MonitorSecondaryText,
                )
                TelemetryTimestamp(record.collectedAt)
            }

            record.sample != null -> {
                TelemetryMetricTiles(record.sample, tcpLatencyMs)
                TelemetryTimestamp(record.collectedAt)
            }
        }
    }
}

@Composable
private fun TelemetryMetricTiles(
    sample: SshTelemetrySample,
    tcpLatencyMs: Long?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MetricTile(
                label = "CPU",
                value = formatPercent(sample.cpuUsagePercent),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "RAM",
                value = formatPercent(sample.memoryUsedPercent),
                detail = "${formatBytesFromKb(sample.memoryUsedKb)} / ${formatBytesFromKb(sample.memoryTotalKb)}",
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MetricTile(
                label = "LOAD",
                value = "${formatDecimal(sample.load1)}/${formatDecimal(sample.load5)}/${formatDecimal(sample.load15)}",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "DYSK",
                value = "${sample.diskUsedPercent}%",
                detail = "${formatBytesFromKb(sample.diskAvailableKb)} wolne",
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MetricTile(
                label = "UPTIME",
                value = formatUptime(sample.uptimeSeconds),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "PING",
                value = tcpLatencyMs?.let { "TCP ${it} ms" } ?: "TCP —",
                detail = compactIcmpLabel(sample.pingStatus, sample.pingMs),
                modifier = Modifier.weight(1f),
            )
        }

        MetricTile(
            label = "SIEĆ",
            value = "↓ ${formatRate(sample.networkRxBytesPerSecond)}   ↑ ${formatRate(sample.networkTxBytesPerSecond)}",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier,
    detail: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MonitorTile),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MonitorSecondaryText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MonitorPrimaryText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            detail?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MonitorSecondaryText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun SshTelemetryAdvancedSettings(
    profile: HostProfile,
    config: HealthMonitorConfig,
    onSave: (HealthMonitorConfig) -> Unit,
) {
    val supported = supportsAutomaticTelemetry(profile)
    var pingTargetInput by remember(config.profileId, config.pingTarget) {
        mutableStateOf(config.pingTarget)
    }
    val parsedPingTarget = TelemetryPingTarget.parse(pingTargetInput)
    val pingTargetValid = !config.pingEnabled || parsedPingTarget != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MonitorPanel),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Diagnostyka sieci",
                color = MonitorPrimaryText,
                fontWeight = FontWeight.Bold,
            )

            if (!supported) {
                Text(
                    if (profile.protocol != ConnectionProtocol.SSH) {
                        "Pełne metryki wymagają profilu SSH."
                    } else {
                        "Profil interaktywny wymaga udziału użytkownika i nie może działać w tle."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Dodatkowy ICMP ping",
                        color = MonitorPrimaryText,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Test z VPS. TCP z telefonu jest mierzony zawsze.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MonitorSecondaryText,
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
                    label = { Text("Cel ICMP") },
                    supportingText = {
                        Text(
                            if (pingTargetValid) {
                                "IPv4 lub DNS, np. 1.1.1.1"
                            } else {
                                "Nieprawidłowy albo niebezpieczny cel."
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
                    Text("Zapisz cel ICMP")
                }
            }
        }
    }
}

internal fun supportsAutomaticTelemetry(profile: HostProfile): Boolean =
    profile.protocol == ConnectionProtocol.SSH &&
        profile.authenticationMethod != AuthenticationMethod.INTERACTIVE

internal fun configForFullManualCheck(
    profile: HostProfile,
    config: HealthMonitorConfig,
): HealthMonitorConfig = config.copy(
    sshTelemetryEnabled = supportsAutomaticTelemetry(profile),
)

@Composable
private fun TelemetryTimestamp(timestamp: Long) {
    Text(
        "Pomiar: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))}",
        style = MaterialTheme.typography.labelSmall,
        color = MonitorSecondaryText,
        maxLines = 1,
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

internal fun compactIcmpLabel(status: TelemetryPingStatus, pingMs: Double?): String? = when (status) {
    TelemetryPingStatus.OK -> pingMs?.let { "ICMP ${formatDecimal(it)} ms" }
    TelemetryPingStatus.DISABLED -> null
    TelemetryPingStatus.UNAVAILABLE -> "ICMP niedostępny"
    TelemetryPingStatus.FAILED -> "ICMP bez odpowiedzi"
}

private const val MAX_PING_TARGET_LENGTH = 253
