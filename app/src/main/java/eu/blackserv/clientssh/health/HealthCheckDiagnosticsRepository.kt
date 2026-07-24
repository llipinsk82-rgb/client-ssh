package eu.blackserv.clientssh.health

enum class HealthCheckRunOutcome {
    RUNNING,
    SUCCESS,
    SKIPPED,
    RETRY,
}

data class HealthCheckRunDiagnostic(
    val profileId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val outcome: HealthCheckRunOutcome = HealthCheckRunOutcome.RUNNING,
    val detail: String = "",
)

class HealthCheckDiagnosticsRepository(
    private val storage: HealthCheckStorage,
) {
    fun get(profileId: String): HealthCheckRunDiagnostic? = synchronized(LOCK) {
        loadAll()[profileId]
    }

    fun getAll(): List<HealthCheckRunDiagnostic> = synchronized(LOCK) {
        loadAll().values.sortedBy { it.profileId }
    }

    fun markStarted(profileId: String, now: Long): HealthCheckRunDiagnostic = synchronized(LOCK) {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        val diagnostic = HealthCheckRunDiagnostic(profileId = profileId, startedAt = now)
        save(diagnostic)
        diagnostic
    }

    fun markFinished(
        profileId: String,
        startedAt: Long,
        finishedAt: Long,
        outcome: HealthCheckRunOutcome,
        detail: String = "",
    ): HealthCheckRunDiagnostic = synchronized(LOCK) {
        require(outcome != HealthCheckRunOutcome.RUNNING) { "Finished run must have a terminal outcome" }
        val diagnostic = HealthCheckRunDiagnostic(
            profileId = profileId,
            startedAt = startedAt,
            finishedAt = finishedAt.coerceAtLeast(startedAt),
            outcome = outcome,
            detail = detail.replace(Regex("[\\r\\n]+"), " ").trim().take(160),
        )
        save(diagnostic)
        diagnostic
    }

    fun remove(profileId: String): Boolean = synchronized(LOCK) {
        val all = loadAll().toMutableMap()
        val removed = all.remove(profileId) != null
        if (removed) persist(all.values)
        removed
    }

    private fun save(diagnostic: HealthCheckRunDiagnostic) {
        val all = loadAll().toMutableMap()
        val current = all[diagnostic.profileId]
        if (current == null || diagnostic.startedAt >= current.startedAt) {
            all[diagnostic.profileId] = diagnostic
            persist(all.values)
        }
    }

    private fun loadAll(): Map<String, HealthCheckRunDiagnostic> =
        DiagnosticsCodec.decode(storage.read()).associateBy { it.profileId }

    private fun persist(items: Collection<HealthCheckRunDiagnostic>) {
        storage.write(DiagnosticsCodec.encode(items))
    }

    private companion object {
        val LOCK = Any()
    }
}

internal object DiagnosticsCodec {
    private const val VERSION = "v1"

    fun encode(items: Collection<HealthCheckRunDiagnostic>): String = buildString {
        appendLine(VERSION)
        items.sortedBy { it.profileId }.forEach { item ->
            append(item.profileId.encodeDiagnosticField())
            append('\t')
            append(item.startedAt)
            append('\t')
            append(item.finishedAt?.toString().orEmpty())
            append('\t')
            append(item.outcome.name)
            append('\t')
            append(item.detail.encodeDiagnosticField())
            appendLine()
        }
    }

    fun decode(raw: String?): List<HealthCheckRunDiagnostic> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.lineSequence().toList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): HealthCheckRunDiagnostic? {
        val fields = line.split('\t')
        if (fields.size != 5) return null
        val profileId = fields[0].decodeDiagnosticField().takeIf { it.isNotBlank() } ?: return null
        val startedAt = fields[1].toLongOrNull() ?: return null
        val finishedAt = fields[2].takeIf { it.isNotBlank() }?.toLongOrNull()
        val outcome = runCatching { HealthCheckRunOutcome.valueOf(fields[3]) }.getOrNull() ?: return null
        return HealthCheckRunDiagnostic(
            profileId = profileId,
            startedAt = startedAt,
            finishedAt = finishedAt,
            outcome = outcome,
            detail = fields[4].decodeDiagnosticField(),
        )
    }

    private fun String.encodeDiagnosticField(): String =
        replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace("\r", "%0D")

    private fun String.decodeDiagnosticField(): String =
        replace("%0D", "\r", ignoreCase = true)
            .replace("%0A", "\n", ignoreCase = true)
            .replace("%09", "\t", ignoreCase = true)
            .replace("%25", "%", ignoreCase = true)
}
