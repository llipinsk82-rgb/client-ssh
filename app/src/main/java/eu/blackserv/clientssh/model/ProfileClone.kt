package eu.blackserv.clientssh.model

import java.util.UUID

internal fun cloneHostProfile(
    source: HostProfile,
    existingNames: Collection<String>,
    newId: String = UUID.randomUUID().toString(),
): HostProfile = source.copy(
    id = newId,
    name = nextCloneName(source.name, existingNames),
)

internal fun nextCloneName(
    sourceName: String,
    existingNames: Collection<String>,
): String {
    val trimmed = sourceName.trim().ifBlank { "Profil" }
    val root = trimmed
        .replace(Regex("\\s+kopia(?:\\s+\\d+)?$", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { trimmed }
    val usedNames = existingNames.mapTo(mutableSetOf()) { it.trim().lowercase() }
    val firstCandidate = "$root kopia"
    if (firstCandidate.lowercase() !in usedNames) return firstCandidate

    var number = 2
    while (true) {
        val candidate = "$root kopia $number"
        if (candidate.lowercase() !in usedNames) return candidate
        number++
    }
}
