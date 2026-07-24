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
        applyTransition(
            ConnectionHistoryStateMachine.begin(
                profile = profile,
                status = status,
                now = System.currentTimeMillis(),
                current = currentEntry,
                persistedOpen = findOpenEntry(profile.id),
                persistedLatestOpen = findLatestOpenEntry(),
            ),
        )
    }

    fun reconnecting(profile: HostProfile, status: String) = synchronized(lock) {
        applyTransition(
            ConnectionHistoryStateMachine.reconnecting(
                profile = profile,
                status = status,
                now = System.currentTimeMillis(),
                current = currentEntry,
                persistedOpen = findOpenEntry(profile.id),
                persistedLatestOpen = findLatestOpenEntry(),
            ),
        )
    }

    fun connected(status: String) = synchronized(lock) {
        applyTransition(
            ConnectionHistoryStateMachine.connected(
                status = status,
                current = currentEntry,
                persistedLatestOpen = findLatestOpenEntry(),
            ),
        )
    }

    fun disconnected(status: String) = finish(ConnectionHistoryResult.DISCONNECTED, status)

    fun error(status: String) = finish(ConnectionHistoryResult.ERROR, status)

    private fun finish(result: ConnectionHistoryResult, status: String) = synchronized(lock) {
        applyTransition(
            ConnectionHistoryStateMachine.finish(
                result = result,
                status = status,
                now = System.currentTimeMillis(),
                current = currentEntry,
                persistedLatestOpen = findLatestOpenEntry(),
            ),
        )
    }

    private fun applyTransition(transition: HistoryTransition) {
        transition.changed.forEach(::persist)
        currentEntry = transition.current
    }

    private fun findOpenEntry(profileId: String): ConnectionHistoryEntry? =
        store?.loadConnectionHistory()?.firstOrNull { it.profileId == profileId && it.finishedAt == null }

    private fun findLatestOpenEntry(): ConnectionHistoryEntry? =
        store?.loadConnectionHistory()?.firstOrNull { it.finishedAt == null }

    private fun persist(entry: ConnectionHistoryEntry) {
        store?.upsertConnectionHistory(entry)
    }
}
