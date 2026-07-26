package eu.blackserv.clientssh.health

import kotlin.math.roundToLong

enum class TelemetryPingStatus {
    OK,
    DISABLED,
    UNAVAILABLE,
    FAILED,
}

enum class SshTelemetryStatus {
    OK,
    PARTIAL,
}

data class SshTelemetrySample(
    val cpuUsagePercent: Double,
    val memoryTotalKb: Long,
    val memoryAvailableKb: Long,
    val load1: Double,
    val load5: Double,
    val load15: Double,
    val diskTotalKb: Long,
    val diskUsedKb: Long,
    val diskAvailableKb: Long,
    val diskUsedPercent: Int,
    val uptimeSeconds: Long,
    val networkRxBytesPerSecond: Long,
    val networkTxBytesPerSecond: Long,
    val pingStatus: TelemetryPingStatus,
    val pingMs: Double?,
) {
    val status: SshTelemetryStatus
        get() = when (pingStatus) {
            TelemetryPingStatus.OK,
            TelemetryPingStatus.DISABLED,
            -> SshTelemetryStatus.OK

            TelemetryPingStatus.UNAVAILABLE,
            TelemetryPingStatus.FAILED,
            -> SshTelemetryStatus.PARTIAL
        }

    val memoryUsedKb: Long
        get() = (memoryTotalKb - memoryAvailableKb).coerceAtLeast(0L)

    val memoryUsedPercent: Double
        get() = if (memoryTotalKb == 0L) 0.0 else memoryUsedKb * 100.0 / memoryTotalKb
}

class SshTelemetryParseException(message: String) : IllegalArgumentException(message)

object SshTelemetryParser {
    const val FORMAT_VERSION = "BLACKSERV_TELEMETRY_V1"
    const val MAX_PAYLOAD_BYTES = 16 * 1024
    private const val MAX_LINES = 40

    private val requiredFields = setOf(
        "CPU_A",
        "CPU_B",
        "MEM_TOTAL_KB",
        "MEM_AVAILABLE_KB",
        "LOAD_1",
        "LOAD_5",
        "LOAD_15",
        "DISK_TOTAL_KB",
        "DISK_USED_KB",
        "DISK_AVAILABLE_KB",
        "DISK_USED_PERCENT",
        "UPTIME_SECONDS",
        "NET_A_RX_BYTES",
        "NET_A_TX_BYTES",
        "NET_B_RX_BYTES",
        "NET_B_TX_BYTES",
        "SAMPLE_MS",
        "PING_STATUS",
    )

    private val allowedFields = requiredFields + "PING_MS"

    fun parse(raw: String): SshTelemetrySample {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            throw SshTelemetryParseException("Odpowiedź telemetryczna jest zbyt duża")
        }

        val lines = raw.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.isEmpty() || lines.first() != FORMAT_VERSION) {
            throw SshTelemetryParseException("Nieznana wersja telemetrii")
        }
        if (lines.size > MAX_LINES) {
            throw SshTelemetryParseException("Odpowiedź telemetryczna ma zbyt wiele pól")
        }

        val values = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.lastIndex) {
                throw SshTelemetryParseException("Nieprawidłowe pole telemetrii")
            }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key !in allowedFields) {
                throw SshTelemetryParseException("Nieznane pole telemetrii")
            }
            if (values.put(key, value) != null) {
                throw SshTelemetryParseException("Powtórzone pole telemetrii")
            }
        }

        if (!values.keys.containsAll(requiredFields)) {
            throw SshTelemetryParseException("Brak wymaganych pól telemetrii")
        }

        val cpuA = parseCpu(values.getValue("CPU_A"))
        val cpuB = parseCpu(values.getValue("CPU_B"))
        val cpuUsage = calculateCpuUsage(cpuA, cpuB)

        val memoryTotal = values.positiveLong("MEM_TOTAL_KB")
        val memoryAvailable = values.nonNegativeLong("MEM_AVAILABLE_KB")
        if (memoryAvailable > memoryTotal) {
            throw SshTelemetryParseException("Nieprawidłowe dane pamięci")
        }

        val diskTotal = values.positiveLong("DISK_TOTAL_KB")
        val diskUsed = values.nonNegativeLong("DISK_USED_KB")
        val diskAvailable = values.nonNegativeLong("DISK_AVAILABLE_KB")
        val diskPercent = values.intInRange("DISK_USED_PERCENT", 0..100)
        if (diskUsed > diskTotal || diskAvailable > diskTotal) {
            throw SshTelemetryParseException("Nieprawidłowe dane dysku")
        }

        val sampleMs = values.longInRange("SAMPLE_MS", 250L..10_000L)
        val rxA = values.nonNegativeLong("NET_A_RX_BYTES")
        val txA = values.nonNegativeLong("NET_A_TX_BYTES")
        val rxB = values.nonNegativeLong("NET_B_RX_BYTES")
        val txB = values.nonNegativeLong("NET_B_TX_BYTES")
        if (rxB < rxA || txB < txA) {
            throw SshTelemetryParseException("Liczniki sieciowe cofnęły się")
        }

        val pingStatus = runCatching {
            TelemetryPingStatus.valueOf(values.getValue("PING_STATUS"))
        }.getOrElse {
            throw SshTelemetryParseException("Nieprawidłowy status ping")
        }
        val pingMs = values["PING_MS"]?.let(::parseFiniteNonNegativeDouble)
        if (pingStatus == TelemetryPingStatus.OK && pingMs == null) {
            throw SshTelemetryParseException("Brak czasu ping")
        }
        if (pingStatus != TelemetryPingStatus.OK && pingMs != null) {
            throw SshTelemetryParseException("Czas ping nie pasuje do statusu")
        }

        return SshTelemetrySample(
            cpuUsagePercent = cpuUsage,
            memoryTotalKb = memoryTotal,
            memoryAvailableKb = memoryAvailable,
            load1 = values.finiteNonNegativeDouble("LOAD_1"),
            load5 = values.finiteNonNegativeDouble("LOAD_5"),
            load15 = values.finiteNonNegativeDouble("LOAD_15"),
            diskTotalKb = diskTotal,
            diskUsedKb = diskUsed,
            diskAvailableKb = diskAvailable,
            diskUsedPercent = diskPercent,
            uptimeSeconds = values.nonNegativeLong("UPTIME_SECONDS"),
            networkRxBytesPerSecond = ratePerSecond(rxB - rxA, sampleMs),
            networkTxBytesPerSecond = ratePerSecond(txB - txA, sampleMs),
            pingStatus = pingStatus,
            pingMs = pingMs,
        )
    }

    private fun parseCpu(raw: String): CpuTimes {
        val values = raw.split(Regex("\\s+")).map { token ->
            token.toLongOrNull()?.takeIf { it >= 0L }
                ?: throw SshTelemetryParseException("Nieprawidłowe liczniki CPU")
        }
        if (values.size != 8) {
            throw SshTelemetryParseException("Nieprawidłowa liczba liczników CPU")
        }
        return CpuTimes(
            user = values[0],
            nice = values[1],
            system = values[2],
            idle = values[3],
            ioWait = values[4],
            irq = values[5],
            softIrq = values[6],
            steal = values[7],
        )
    }

    private fun calculateCpuUsage(before: CpuTimes, after: CpuTimes): Double {
        val totalBefore = before.total()
        val totalAfter = after.total()
        val idleBefore = before.idleTotal()
        val idleAfter = after.idleTotal()
        if (totalAfter <= totalBefore || idleAfter < idleBefore) {
            throw SshTelemetryParseException("Nieprawidłowa próbka CPU")
        }
        val totalDelta = totalAfter - totalBefore
        val idleDelta = idleAfter - idleBefore
        if (idleDelta > totalDelta) {
            throw SshTelemetryParseException("Nieprawidłowa bezczynność CPU")
        }
        return ((totalDelta - idleDelta) * 100.0 / totalDelta).coerceIn(0.0, 100.0)
    }

    private fun ratePerSecond(delta: Long, sampleMs: Long): Long =
        (delta.toDouble() * 1_000.0 / sampleMs.toDouble()).roundToLong().coerceAtLeast(0L)

    private fun Map<String, String>.positiveLong(key: String): Long =
        getValue(key).toLongOrNull()?.takeIf { it > 0L }
            ?: throw SshTelemetryParseException("Nieprawidłowa wartość $key")

    private fun Map<String, String>.nonNegativeLong(key: String): Long =
        getValue(key).toLongOrNull()?.takeIf { it >= 0L }
            ?: throw SshTelemetryParseException("Nieprawidłowa wartość $key")

    private fun Map<String, String>.longInRange(key: String, range: LongRange): Long =
        getValue(key).toLongOrNull()?.takeIf { it in range }
            ?: throw SshTelemetryParseException("Nieprawidłowa wartość $key")

    private fun Map<String, String>.intInRange(key: String, range: IntRange): Int =
        getValue(key).toIntOrNull()?.takeIf { it in range }
            ?: throw SshTelemetryParseException("Nieprawidłowa wartość $key")

    private fun Map<String, String>.finiteNonNegativeDouble(key: String): Double =
        parseFiniteNonNegativeDouble(getValue(key))

    private fun parseFiniteNonNegativeDouble(raw: String): Double =
        raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it <= 1_000_000.0 }
            ?: throw SshTelemetryParseException("Nieprawidłowa wartość zmiennoprzecinkowa")

    private data class CpuTimes(
        val user: Long,
        val nice: Long,
        val system: Long,
        val idle: Long,
        val ioWait: Long,
        val irq: Long,
        val softIrq: Long,
        val steal: Long,
    ) {
        fun total(): Long = listOf(user, nice, system, idle, ioWait, irq, softIrq, steal)
            .fold(0L) { sum, value -> Math.addExact(sum, value) }

        fun idleTotal(): Long = Math.addExact(idle, ioWait)
    }
}
