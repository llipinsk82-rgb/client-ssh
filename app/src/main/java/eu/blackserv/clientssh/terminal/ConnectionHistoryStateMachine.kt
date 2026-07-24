package eu.blackserv.clientssh.terminal

import eu.blackserv.clientssh.model.ConnectionHistoryEntry
import eu.blackserv.clientssh.model.ConnectionHistoryResult
import eu.blackserv.clientssh.model.HostProfile

data class HistoryTransition(
    val current: ConnectionHistoryEntry?,
    val changed: List<ConnectionHistoryEntry>,
)

object ConnectionHistoryStateMachine {
    fun begin(
        profile: HostProfile,
        status: String,
        now: Long,
        current: ConnectionHistoryEntry?,
        persistedOpen: ConnectionHistoryEntry?,
    ): HistoryTransition {
        val sameProfile = current
            ?.takeIf { it.profileId == profile.id && it.finishedAt == null }
            ?: persistedOpen?.takeIf { it.profileId == profile.id && it.finishedAt == null }

        if (sameProfile != null) {
            val updated = sameProfile.copy(message = status)
            return HistoryTransition(updated, listOf(updated))
        }

        val changed = mutableListOf<ConnectionHistoryEntry>()
        current?.takeIf { it.finishedAt == null && it.profileId != profile.id }?.let { previous ->
            changed += previous.copy(
                finishedAt = now,
                result = ConnectionHistoryResult.DISCONNECTED,
                message = "Uruchomiono inną sesję.",
            )
        }

        val created = ConnectionHistoryEntry(
            profileId = profile.id,
            profileName = profile.name,
            host = profile.host,
            port = profile.port,
            username = profile.username,
            protocol = profile.protocol,
            startedAt = now,
            result = ConnectionHistoryResult.CONNECTED,
            message = status,
        )
        changed += created
        return HistoryTransition(created, changed)
    }

    fun reconnecting(
        profile: HostProfile,
        status: String,
        now: Long,
        current: ConnectionHistoryEntry?,
        persistedOpen: ConnectionHistoryEntry?,
    ): HistoryTransition = begin(profile, status, now, current, persistedOpen)

    fun connected(
        status: String,
        current: ConnectionHistoryEntry?,
        persistedLatestOpen: ConnectionHistoryEntry?,
    ): HistoryTransition {
        val entry = current ?: persistedLatestOpen ?: return HistoryTransition(null, emptyList())
        val updated = entry.copy(
            finishedAt = null,
            result = ConnectionHistoryResult.CONNECTED,
            message = status,
        )
        return HistoryTransition(updated, listOf(updated))
    }

    fun finish(
        result: ConnectionHistoryResult,
        status: String,
        now: Long,
        current: ConnectionHistoryEntry?,
        persistedLatestOpen: ConnectionHistoryEntry?,
    ): HistoryTransition {
        val entry = current ?: persistedLatestOpen ?: return HistoryTransition(null, emptyList())
        val finished = entry.copy(
            finishedAt = now,
            result = result,
            message = status,
        )
        return HistoryTransition(null, listOf(finished))
    }
}
