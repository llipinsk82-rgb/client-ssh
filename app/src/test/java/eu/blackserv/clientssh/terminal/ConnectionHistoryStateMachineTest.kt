package eu.blackserv.clientssh.terminal

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionHistoryEntry
import eu.blackserv.clientssh.model.ConnectionHistoryResult
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionHistoryStateMachineTest {
    @Test
    fun beginReusesOpenEntryForSameProfile() {
        val profile = profile("one")
        val open = entry(profile, startedAt = 100L)

        val transition = ConnectionHistoryStateMachine.begin(
            profile = profile,
            status = "Ponowne łączenie…",
            now = 200L,
            current = null,
            persistedOpen = open,
            persistedLatestOpen = open,
        )

        assertEquals(open.id, transition.current?.id)
        assertEquals(1, transition.changed.size)
        assertEquals("Ponowne łączenie…", transition.changed.single().message)
        assertNull(transition.changed.single().finishedAt)
    }

    @Test
    fun beginClosesPreviousProfileBeforeCreatingNewEntry() {
        val first = profile("one")
        val second = profile("two")
        val open = entry(first, startedAt = 100L)

        val transition = ConnectionHistoryStateMachine.begin(
            profile = second,
            status = "Łączenie…",
            now = 300L,
            current = open,
            persistedOpen = null,
            persistedLatestOpen = open,
        )

        assertEquals(2, transition.changed.size)
        assertEquals(ConnectionHistoryResult.DISCONNECTED, transition.changed[0].result)
        assertEquals(300L, transition.changed[0].finishedAt)
        assertEquals(second.id, transition.current?.profileId)
        assertNull(transition.current?.finishedAt)
    }

    @Test
    fun beginAfterProcessRestartClosesPersistedOtherProfile() {
        val first = profile("one")
        val second = profile("two")
        val persistedOpen = entry(first, startedAt = 100L)

        val transition = ConnectionHistoryStateMachine.begin(
            profile = second,
            status = "Łączenie…",
            now = 450L,
            current = null,
            persistedOpen = null,
            persistedLatestOpen = persistedOpen,
        )

        assertEquals(2, transition.changed.size)
        assertEquals(first.id, transition.changed[0].profileId)
        assertEquals(450L, transition.changed[0].finishedAt)
        assertEquals(ConnectionHistoryResult.DISCONNECTED, transition.changed[0].result)
        assertEquals("Uruchomiono inną sesję.", transition.changed[0].message)
        assertEquals(second.id, transition.current?.profileId)
        assertNull(transition.current?.finishedAt)
    }

    @Test
    fun reconnectKeepsSameSessionEntry() {
        val profile = profile("one")
        val open = entry(profile, startedAt = 100L)

        val transition = ConnectionHistoryStateMachine.reconnecting(
            profile = profile,
            status = "Ponawiam za 5 s",
            now = 500L,
            current = open,
            persistedOpen = null,
            persistedLatestOpen = open,
        )

        assertEquals(open.id, transition.current?.id)
        assertEquals(100L, transition.current?.startedAt)
        assertNull(transition.current?.finishedAt)
    }

    @Test
    fun finishClosesOpenEntryWithError() {
        val profile = profile("one")
        val open = entry(profile, startedAt = 100L)

        val transition = ConnectionHistoryStateMachine.finish(
            result = ConnectionHistoryResult.ERROR,
            status = "Auth fail",
            now = 900L,
            current = open,
            persistedLatestOpen = null,
        )

        assertNull(transition.current)
        assertEquals(1, transition.changed.size)
        assertEquals(ConnectionHistoryResult.ERROR, transition.changed.single().result)
        assertEquals(900L, transition.changed.single().finishedAt)
        assertEquals("Auth fail", transition.changed.single().message)
    }

    private fun profile(id: String) = HostProfile(
        id = id,
        name = "Host $id",
        host = "$id.example.com",
        port = 22,
        username = "root",
        protocol = ConnectionProtocol.SSH,
        authenticationMethod = AuthenticationMethod.PASSWORD,
    )

    private fun entry(profile: HostProfile, startedAt: Long) = ConnectionHistoryEntry(
        profileId = profile.id,
        profileName = profile.name,
        host = profile.host,
        port = profile.port,
        username = profile.username,
        protocol = profile.protocol,
        startedAt = startedAt,
        result = ConnectionHistoryResult.CONNECTED,
        message = "Połączono",
    )
}
