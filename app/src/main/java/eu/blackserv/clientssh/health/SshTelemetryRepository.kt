package eu.blackserv.clientssh.health

enum class SshTelemetryRecordOutcome {
    SUCCESS,
    FAILURE,
}

data class SshTelemetryRecord(
    val profileId: String,
    val collectedAt: Long,
    val outcome: SshTelemetryRecordOutcome,
    val sample: SshTelemetrySample?,
    val failureKind: SshTelemetryFailureKind?,
    val message: String,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(collectedAt >= 0L) { "collectedAt must not be negative" }
        when (outcome) {
            SshTelemetryRecordOutcome.SUCCESS -> {
                require(sample != null) { "success requires sample" }
                require(failureKind == null) { "success must not contain failure kind" }
            }

            SshTelemetryRecordOutcome.FAILURE -> {
                require(sample == null) { "failure must not contain sample" }
                require(failureKind != null) { "failure requires failure kind" }
            }
        }
    }

    companion object {
        fun success(profileId: String, collectedAt: Long, sample: SshTelemetrySample) =
            SshTelemetryRecord(
                profileId = profileId,
                collectedAt = collectedAt,
                outcome = SshTelemetryRecordOutcome.SUCCESS,
                sample = sample,
                failureKind = null,
                message = when (sample.status) {
                    SshTelemetryStatus.OK -> "Telemetria SSH zakończona"
                    SshTelemetryStatus.PARTIAL -> "Telemetria SSH częściowa"
                },
            )

        fun failure(
            profileId: String,
            collectedAt: Long,
            kind: SshTelemetryFailureKind,
            message: String,
        ) = SshTelemetryRecord(
            profileId = profileId,
            collectedAt = collectedAt,
            outcome = SshTelemetryRecordOutcome.FAILURE,
            sample = null,
            failureKind = kind,
            message = message.toSafeTelemetryMessage(),
        )
    }
}

class SshTelemetryRepository(
    private val storage: HealthCheckStorage,
) {
    fun append(record: SshTelemetryRecord): SshTelemetryRecord = synchronized(STORAGE_LOCK) {
        val all = loadAll().toMutableList()
        all += record
        persist(trim(all))
        record
    }

    fun latest(profileId: String): SshTelemetryRecord? = synchronized(STORAGE_LOCK) {
        loadAll()
            .asSequence()
            .filter { it.profileId == profileId }
            .maxByOrNull { it.collectedAt }
    }

    fun latestAll(): List<SshTelemetryRecord> = synchronized(STORAGE_LOCK) {
        loadAll()
            .groupBy { it.profileId }
            .values
            .mapNotNull { records -> records.maxByOrNull { it.collectedAt } }
            .sortedBy { it.profileId }
    }

    fun history(profileId: String, limit: Int = DEFAULT_HISTORY_LIMIT): List<SshTelemetryRecord> =
        synchronized(STORAGE_LOCK) {
            require(limit in 1..MAX_RECORDS_PER_PROFILE) { "limit poza zakresem" }
            loadAll()
                .asSequence()
                .filter { it.profileId == profileId }
                .sortedByDescending { it.collectedAt }
                .take(limit)
                .toList()
        }

    fun remove(profileId: String): Boolean = synchronized(STORAGE_LOCK) {
        val all = loadAll()
        val remaining = all.filterNot { it.profileId == profileId }
        val removed = remaining.size != all.size
        if (removed) persist(remaining)
        removed
    }

    private fun loadAll(): List<SshTelemetryRecord> = SshTelemetryRecordCodec.decode(storage.read())

    private fun persist(records: Collection<SshTelemetryRecord>) {
        storage.write(SshTelemetryRecordCodec.encode(records))
    }

    private fun trim(records: List<SshTelemetryRecord>): List<SshTelemetryRecord> =
        records
            .groupBy { it.profileId }
            .values
            .flatMap { profileRecords ->
                profileRecords.sortedByDescending { it.collectedAt }.take(MAX_RECORDS_PER_PROFILE)
            }

    companion object {
        const val MAX_RECORDS_PER_PROFILE = 96
        const val DEFAULT_HISTORY_LIMIT = 24
        private val STORAGE_LOCK = Any()
    }
}

internal object SshTelemetryRecordCodec {
    private const val VERSION = "v1"
    private const val FIELD_COUNT = 20

    fun encode(records: Collection<SshTelemetryRecord>): String = buildString {
        appendLine(VERSION)
        records
            .sortedWith(compareBy<SshTelemetryRecord> { it.profileId }.thenByDescending { it.collectedAt })
            .forEach { record ->
                val sample = record.sample
                val fields = listOf(
                    record.profileId.encodeTelemetryField(),
                    record.collectedAt.toString(),
                    record.outcome.name,
                    sample?.cpuUsagePercent?.toString().orEmpty(),
                    sample?.memoryTotalKb?.toString().orEmpty(),
                    sample?.memoryAvailableKb?.toString().orEmpty(),
                    sample?.load1?.toString().orEmpty(),
                    sample?.load5?.toString().orEmpty(),
                    sample?.load15?.toString().orEmpty(),
                    sample?.diskTotalKb?.toString().orEmpty(),
                    sample?.diskUsedKb?.toString().orEmpty(),
                    sample?.diskAvailableKb?.toString().orEmpty(),
                    sample?.diskUsedPercent?.toString().orEmpty(),
                    sample?.uptimeSeconds?.toString().orEmpty(),
                    sample?.networkRxBytesPerSecond?.toString().orEmpty(),
                    sample?.networkTxBytesPerSecond?.toString().orEmpty(),
                    sample?.pingStatus?.name.orEmpty(),
                    sample?.pingMs?.toString().orEmpty(),
                    record.failureKind?.name.orEmpty(),
                    record.message.toSafeTelemetryMessage().encodeTelemetryField(),
                )
                appendLine(fields.joinToString("\t"))
            }
    }

    fun decode(raw: String?): List<SshTelemetryRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.lineSequence().toList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): SshTelemetryRecord? {
        if (line.isBlank()) return null
        val fields = line.split('\t')
        if (fields.size != FIELD_COUNT) return null
        val profileId = fields[0].decodeTelemetryField().takeIf(String::isNotBlank) ?: return null
        val collectedAt = fields[1].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val outcome = enumValueOrNull<SshTelemetryRecordOutcome>(fields[2]) ?: return null
        val message = fields[19].decodeTelemetryField().toSafeTelemetryMessage()

        return runCatching {
            when (outcome) {
                SshTelemetryRecordOutcome.SUCCESS -> SshTelemetryRecord.success(
                    profileId = profileId,
                    collectedAt = collectedAt,
                    sample = decodeSample(fields),
                ).copy(message = message.ifBlank { "Telemetria SSH zakończona" })

                SshTelemetryRecordOutcome.FAILURE -> SshTelemetryRecord.failure(
                    profileId = profileId,
                    collectedAt = collectedAt,
                    kind = enumValueOrNull<SshTelemetryFailureKind>(fields[18])
                        ?: error("Brak kodu błędu"),
                    message = message,
                )
            }
        }.getOrNull()
    }

    private fun decodeSample(fields: List<String>): SshTelemetrySample {
        val cpu = fields[3].requiredFiniteDouble(0.0..100.0)
        val memoryTotal = fields[4].requiredPositiveLong()
        val memoryAvailable = fields[5].requiredNonNegativeLong().also {
            require(it <= memoryTotal)
        }
        val diskTotal = fields[9].requiredPositiveLong()
        val diskUsed = fields[10].requiredNonNegativeLong().also { require(it <= diskTotal) }
        val diskAvailable = fields[11].requiredNonNegativeLong().also { require(it <= diskTotal) }
        val pingStatus = enumValueOrNull<TelemetryPingStatus>(fields[16]) ?: error("Brak statusu ping")
        val pingMs = fields[17].takeIf(String::isNotBlank)?.requiredFiniteDouble(0.0..60_000.0)
        require((pingStatus == TelemetryPingStatus.OK) == (pingMs != null))

        return SshTelemetrySample(
            cpuUsagePercent = cpu,
            memoryTotalKb = memoryTotal,
            memoryAvailableKb = memoryAvailable,
            load1 = fields[6].requiredFiniteDouble(0.0..1_000_000.0),
            load5 = fields[7].requiredFiniteDouble(0.0..1_000_000.0),
            load15 = fields[8].requiredFiniteDouble(0.0..1_000_000.0),
            diskTotalKb = diskTotal,
            diskUsedKb = diskUsed,
            diskAvailableKb = diskAvailable,
            diskUsedPercent = fields[12].toIntOrNull()?.takeIf { it in 0..100 }
                ?: error("Nieprawidłowy procent dysku"),
            uptimeSeconds = fields[13].requiredNonNegativeLong(),
            networkRxBytesPerSecond = fields[14].requiredNonNegativeLong(),
            networkTxBytesPerSecond = fields[15].requiredNonNegativeLong(),
            pingStatus = pingStatus,
            pingMs = pingMs,
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? =
        runCatching { enumValueOf<T>(raw) }.getOrNull()

    private fun String.requiredPositiveLong(): Long =
        toLongOrNull()?.takeIf { it > 0L } ?: error("Nieprawidłowa liczba dodatnia")

    private fun String.requiredNonNegativeLong(): Long =
        toLongOrNull()?.takeIf { it >= 0L } ?: error("Nieprawidłowa liczba")

    private fun String.requiredFiniteDouble(range: ClosedFloatingPointRange<Double>): Double =
        toDoubleOrNull()?.takeIf { it.isFinite() && it in range }
            ?: error("Nieprawidłowa liczba zmiennoprzecinkowa")
}

private fun String.toSafeTelemetryMessage(): String =
    replace(Regex("[\\r\\n\\t]+"), " ").trim().take(200)

private fun String.encodeTelemetryField(): String = buildString {
    this@encodeTelemetryField.forEach { char ->
        when (char) {
            '%' -> append("%25")
            '\t' -> append("%09")
            '\n' -> append("%0A")
            '\r' -> append("%0D")
            else -> append(char)
        }
    }
}

private fun String.decodeTelemetryField(): String {
    val output = StringBuilder()
    var index = 0
    while (index < length) {
        if (this[index] == '%' && index + 2 < length) {
            val decoded = when (substring(index + 1, index + 3).uppercase()) {
                "25" -> '%'
                "09" -> '\t'
                "0A" -> '\n'
                "0D" -> '\r'
                else -> null
            }
            if (decoded != null) {
                output.append(decoded)
                index += 3
                continue
            }
        }
        output.append(this[index])
        index++
    }
    return output.toString()
}
