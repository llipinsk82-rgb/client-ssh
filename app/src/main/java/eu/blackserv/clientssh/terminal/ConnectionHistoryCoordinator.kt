package eu.blackserv.clientssh.terminal

import eu.blackserv.clientssh.model.ConnectionHistoryEntry
import eu.blackserv.clientssh.model.ConnectionHistoryResult
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.storage.LocalAppStore

object ConnectionHistoryCoordinator {
    @Volatile
    private var store: LocalAppStore? = null

    private val lock = Any()
    private var currentEntry: ConnectionHistoryEntry? = null

    fun initialize(appStore: LocalAppStore) {
        store = appStore
    }

    fun begin(profile: HostProfile, status: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val existing = findOpenEntry(profile.id)
        if (existing != null) {
            currentEntry = existing.copy(message = status).also(::persist)
            return@synchronized
        }

        currentEntry?.takeIf { it.finishedAt == null && it.profileId != profile.id }?.let { previous ->
            persist(
                previous.copy(
                    finishedAt = now,
                    result = ConnectionHistoryResult.DISCONNECTED,
                    message = "Uruchomiono inną sesję.",
                ),
            )
        }

        currentEntry = ConnectionHistoryEntry(
            profileId = profile.id,
            profileName = profile.name,
            host = profile.host,
            port = profile.port,
            username = profile.username,
            protocol = profile.protocol,
            startedAt = now,
            result = ConnectionHistoryResult.CONNECTED,
            message = status,
        ).also(::persist)
    }

    fun reconnecting(profile: HostProfile, status: String) = synchronized(lock) {
        val entry = currentEntry
            ?.takeIf { it.profileId == profile.id && it.finishedAt == null }
            ?: findOpenEntry(profile.id)

        if (entry != null) {
            currentEntry = entry.copy(message = status).also(::persist)
        } else {
            begin(profile, status)
        }
    }

    fun connected(status: String) = synchronized(lock) {
        val entry = currentEntry ?: findLatestOpenEntry() ?: return@synchronized
        currentEntry = entry.copy(
            finishedAt = null,
            result = ConnectionHistoryResult.CONNECTED,
            message = status,
        ).also(::persist)
    }

    fun disconnected(status: String) = finish(ConnectionHistoryResult.DISCONNECTED, status)

    fun error(status: String) = finish(ConnectionHistoryResult.ERROR, status)

    private fun finish(result: ConnectionHistoryResult, status: String) = synchronized(lock) {
        val entry = currentEntry ?: findLatestOpenEntry() ?: return@synchronized
        persist(
            entry.copy(
                finishedAt = System.currentTimeMillis(),
                result = result,
                message = status,
            ),
        )
        currentEntry = null
    }

    private fun findOpenEntry(profileId: String): ConnectionHistoryEntry? =
        store?.loadConnectionHistory()?.firstOrNull { it.profileId == profileId && it.finishedAt == null }

    private fun findLatestOpenEntry(): ConnectionHistoryEntry? =
        store?.loadConnectionHistory()?.firstOrNull { it.finishedAt == null }

    private fun persist(entry: ConnectionHistoryEntry) {
        val appStore = store ?: return
        val history = appStore.loadConnectionHistory().toMutableList()
        val index = history.indexOfFirst { it.id == entry.id }
        if (index >= 0) history[index] = entry else history.add(0, entry)
        appStore.saveConnectionHistory(history)
    }
}
