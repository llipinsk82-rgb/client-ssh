package eu.blackserv.clientssh.health

data class HealthCheckRecord(
    val profileId: String,
    val checkedAt: Long,
    val status: HealthStatus,
    val responseTimeMs: Long? = null,
    val message: String = "",
)

class HealthCheckHistoryRepository(
    private val storage: HealthCheckStorage,
    private val maxEntriesPerProfile: Int = DEFAULT_MAX_ENTRIES_PER_PROFILE,
) {
    init {
        require(maxEntriesPerProfile > 0) { "maxEntriesPerProfile must be greater than zero" }
    }

    fun get(profileId: String): List<HealthCheckRecord> = synchronized(STORAGE_LOCK) {
        loadAll().filter { it.profileId == profileId }.sortedByDescending { it.checkedAt }
    }

    fun append(record: HealthCheckRecord) = synchronized(STORAGE_LOCK) {
        require(record.profileId.isNotBlank()) { "profileId must not be blank" }
        val retained = (loadAll() + record)
            .groupBy { it.profileId }
            .flatMap { (_, records) -> records.sortedByDescending { it.checkedAt }.take(maxEntriesPerProfile) }
        storage.write(HealthCheckHistoryCodec.encode(retained))
    }

    fun removeProfile(profileId: String): Boolean = synchronized(STORAGE_LOCK) {
        val all = loadAll()
        val retained = all.filterNot { it.profileId == profileId }
        if (retained.size == all.size) return false
        storage.write(HealthCheckHistoryCodec.encode(retained))
        true
    }

    private fun loadAll(): List<HealthCheckRecord> = HealthCheckHistoryCodec.decode(storage.read())

    companion object {
        const val DEFAULT_MAX_ENTRIES_PER_PROFILE = 100
        private val STORAGE_LOCK = Any()
    }
}

internal object HealthCheckHistoryCodec {
    private const val VERSION = "v1"

    fun encode(records: Collection<HealthCheckRecord>): String = buildString {
        appendLine(VERSION)
        records.sortedWith(compareBy<HealthCheckRecord> { it.profileId }.thenByDescending { it.checkedAt })
            .forEach { record ->
                append(record.profileId.escapeHistoryField())
                append('\t')
                append(record.checkedAt)
                append('\t')
                append(record.status.name)
                append('\t')
                append(record.responseTimeMs?.toString().orEmpty())
                append('\t')
                append(record.message.escapeHistoryField())
                appendLine()
            }
    }

    fun decode(raw: String?): List<HealthCheckRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.lineSequence().toList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): HealthCheckRecord? {
        val fields = line.split('\t')
        if (fields.size != 5) return null
        val profileId = fields[0].unescapeHistoryField().takeIf { it.isNotBlank() } ?: return null
        val checkedAt = fields[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val status = runCatching { HealthStatus.valueOf(fields[2]) }.getOrNull() ?: return null
        val responseTimeMs = fields[3].takeIf { it.isNotBlank() }?.toLongOrNull()?.takeIf { it >= 0 } ?: if (fields[3].isBlank()) null else return null
        return HealthCheckRecord(profileId, checkedAt, status, responseTimeMs, fields[4].unescapeHistoryField())
    }

    private fun String.escapeHistoryField(): String = replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace("\r", "%0D")
    private fun String.unescapeHistoryField(): String = replace("%0D", "\r", true).replace("%0A", "\n", true).replace("%09", "\t", true).replace("%25", "%", true)
}
