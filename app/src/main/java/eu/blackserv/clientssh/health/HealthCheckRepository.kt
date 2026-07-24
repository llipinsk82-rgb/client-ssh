package eu.blackserv.clientssh.health

interface HealthCheckStorage {
    fun read(): String?
    fun write(value: String)
}

class HealthCheckRepository(
    private val storage: HealthCheckStorage,
) {
    fun get(profileId: String): HealthCheckSnapshot? = synchronized(REPOSITORY_LOCK) {
        loadAll()[profileId]
    }

    fun getAll(): List<HealthCheckSnapshot> = synchronized(REPOSITORY_LOCK) {
        loadAll().values.sortedBy { it.profileId }
    }

    fun upsert(snapshot: HealthCheckSnapshot): HealthCheckSnapshot = synchronized(REPOSITORY_LOCK) {
        require(snapshot.profileId.isNotBlank()) { "profileId must not be blank" }
        val all = loadAll().toMutableMap()
        all[snapshot.profileId] = snapshot
        persist(all)
        snapshot
    }

    fun remove(profileId: String): Boolean = synchronized(REPOSITORY_LOCK) {
        val all = loadAll().toMutableMap()
        val removed = all.remove(profileId) != null
        if (removed) persist(all)
        removed
    }

    fun applyObservation(
        profileId: String,
        observation: HealthObservation,
        now: Long,
        offlineFailureThreshold: Int,
    ): HealthCheckTransition = synchronized(REPOSITORY_LOCK) {
        val all = loadAll().toMutableMap()
        val transition = HealthCheckStateMachine.apply(
            profileId = profileId,
            current = all[profileId],
            observation = observation,
            now = now,
            offlineFailureThreshold = offlineFailureThreshold,
        )
        all[profileId] = transition.snapshot
        persist(all)
        transition
    }

    private fun loadAll(): Map<String, HealthCheckSnapshot> =
        HealthCheckSnapshotCodec.decode(storage.read()).associateBy { it.profileId }

    private fun persist(items: Map<String, HealthCheckSnapshot>) {
        storage.write(HealthCheckSnapshotCodec.encode(items.values))
    }

    private companion object {
        // Repository instances can coexist in workers, activities and after process recreation.
        // A process-wide lock prevents independent read-modify-write cycles from losing data.
        val REPOSITORY_LOCK = Any()
    }
}

internal object HealthCheckSnapshotCodec {
    private const val VERSION = "v1"
    private const val FIELD_SEPARATOR = '\t'

    fun encode(items: Collection<HealthCheckSnapshot>): String = buildString {
        appendLine(VERSION)
        items.sortedBy { it.profileId }.forEach { item ->
            append(
                listOf(
                    encodeField(item.profileId),
                    item.status.name,
                    item.consecutiveFailures.toString(),
                    item.lastCheckedAt?.toString().orEmpty(),
                    item.lastSuccessAt?.toString().orEmpty(),
                    item.responseTimeMs?.toString().orEmpty(),
                    encodeField(item.message),
                ).joinToString(FIELD_SEPARATOR.toString()),
            )
            appendLine()
        }
    }

    fun decode(raw: String?): List<HealthCheckSnapshot> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.lineSequence().toList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).mapNotNull(::decodeLine)
    }

    private fun decodeLine(line: String): HealthCheckSnapshot? {
        if (line.isBlank()) return null
        val fields = line.split(FIELD_SEPARATOR)
        if (fields.size != 7) return null
        val profileId = decodeField(fields[0]).takeIf { it.isNotBlank() } ?: return null
        val status = runCatching { HealthStatus.valueOf(fields[1]) }.getOrNull() ?: return null
        val failures = fields[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return HealthCheckSnapshot(
            profileId = profileId,
            status = status,
            consecutiveFailures = failures,
            lastCheckedAt = fields[3].toLongOrNull(),
            lastSuccessAt = fields[4].toLongOrNull(),
            responseTimeMs = fields[5].toLongOrNull(),
            message = decodeField(fields[6]),
        )
    }

    private fun encodeField(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '%' -> append("%25")
                '\t' -> append("%09")
                '\n' -> append("%0A")
                '\r' -> append("%0D")
                else -> append(char)
            }
        }
    }

    private fun decodeField(value: String): String {
        val output = StringBuilder()
        var index = 0
        while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
                val code = value.substring(index + 1, index + 3)
                val decoded = when (code.uppercase()) {
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
            output.append(value[index])
            index++
        }
        return output.toString()
    }
}
