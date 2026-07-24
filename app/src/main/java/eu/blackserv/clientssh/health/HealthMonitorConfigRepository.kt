package eu.blackserv.clientssh.health

data class HealthMonitorConfig(
    val profileId: String,
    val enabled: Boolean = false,
    val intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    val offlineFailureThreshold: Int = DEFAULT_OFFLINE_FAILURE_THRESHOLD,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(intervalMinutes >= MIN_INTERVAL_MINUTES) {
            "intervalMinutes must be at least $MIN_INTERVAL_MINUTES"
        }
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            "timeoutMs must be between $MIN_TIMEOUT_MS and $MAX_TIMEOUT_MS"
        }
        require(offlineFailureThreshold in 1..MAX_OFFLINE_FAILURE_THRESHOLD) {
            "offlineFailureThreshold must be between 1 and $MAX_OFFLINE_FAILURE_THRESHOLD"
        }
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15L
        const val DEFAULT_INTERVAL_MINUTES = 15L
        const val MIN_TIMEOUT_MS = 500
        const val MAX_TIMEOUT_MS = 30_000
        const val DEFAULT_TIMEOUT_MS = 5_000
        const val MAX_OFFLINE_FAILURE_THRESHOLD = 20
        const val DEFAULT_OFFLINE_FAILURE_THRESHOLD = 3
    }
}

class HealthMonitorConfigRepository(
    private val storage: HealthCheckStorage,
) {
    fun get(profileId: String): HealthMonitorConfig? = synchronized(STORAGE_LOCK) {
        loadAll()[profileId]
    }

    fun getAll(): List<HealthMonitorConfig> = synchronized(STORAGE_LOCK) {
        loadAll().values.sortedBy { it.profileId }
    }

    fun getEnabled(): List<HealthMonitorConfig> = synchronized(STORAGE_LOCK) {
        loadAll().values.filter { it.enabled }.sortedBy { it.profileId }
    }

    fun upsert(config: HealthMonitorConfig): HealthMonitorConfig = synchronized(STORAGE_LOCK) {
        val all = loadAll().toMutableMap()
        all[config.profileId] = config
        persist(all)
        config
    }

    fun setEnabled(profileId: String, enabled: Boolean): HealthMonitorConfig = synchronized(STORAGE_LOCK) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        val all = loadAll().toMutableMap()
        val updated = (all[profileId] ?: HealthMonitorConfig(profileId = profileId)).copy(enabled = enabled)
        all[profileId] = updated
        persist(all)
        updated
    }

    fun remove(profileId: String): Boolean = synchronized(STORAGE_LOCK) {
        val all = loadAll().toMutableMap()
        val removed = all.remove(profileId) != null
        if (removed) persist(all)
        removed
    }

    private fun loadAll(): Map<String, HealthMonitorConfig> =
        HealthMonitorConfigCodec.decode(storage.read()).associateBy { it.profileId }

    private fun persist(items: Map<String, HealthMonitorConfig>) {
        storage.write(HealthMonitorConfigCodec.encode(items.values))
    }

    private companion object {
        val STORAGE_LOCK = Any()
    }
}

internal object HealthMonitorConfigCodec {
    private const val VERSION = "v1"

    fun encode(items: Collection<HealthMonitorConfig>): String = buildString {
        appendLine(VERSION)
        items.sortedBy { it.profileId }.forEach { item ->
            append(item.profileId.encodeField())
            append('\t')
            append(item.enabled)
            append('\t')
            append(item.intervalMinutes)
            append('\t')
            append(item.timeoutMs)
            append('\t')
            append(item.offlineFailureThreshold)
            appendLine()
        }
    }

    fun decode(raw: String?): List<HealthMonitorConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.lineSequence().toList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): HealthMonitorConfig? {
        if (line.isBlank()) return null
        val fields = line.split('\t')
        if (fields.size != 5) return null
        val profileId = fields[0].decodeField().takeIf { it.isNotBlank() } ?: return null
        val enabled = fields[1].toBooleanStrictOrNull() ?: return null
        val interval = fields[2].toLongOrNull() ?: return null
        val timeout = fields[3].toIntOrNull() ?: return null
        val threshold = fields[4].toIntOrNull() ?: return null
        return runCatching {
            HealthMonitorConfig(
                profileId = profileId,
                enabled = enabled,
                intervalMinutes = interval,
                timeoutMs = timeout,
                offlineFailureThreshold = threshold,
            )
        }.getOrNull()
    }

    private fun String.encodeField(): String = buildString {
        this@encodeField.forEach { char ->
            when (char) {
                '%' -> append("%25")
                '\t' -> append("%09")
                '\n' -> append("%0A")
                '\r' -> append("%0D")
                else -> append(char)
            }
        }
    }

    private fun String.decodeField(): String {
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
}
