package eu.blackserv.clientssh.terminal

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalSessionBusTest {
    private val profile = HostProfile(
        id = "profile-active",
        name = "Active server",
        host = "example.test",
        port = 22,
        username = "tester",
        protocol = ConnectionProtocol.SSH,
        authenticationMethod = AuthenticationMethod.PASSWORD,
    )

    @After
    fun cleanUp() {
        TerminalSessionBus.markDisconnected("Test cleanup")
        TerminalSessionBus.clearLocalBuffer()
    }

    @Test
    fun beginningSameActiveProfileDoesNotDetachWriterOrResetSnapshot() {
        TerminalSessionBus.begin(profile)
        TerminalSessionBus.markConnected("SSH connected")

        var sent = ""
        TerminalSessionBus.attachWriter { bytes -> sent += bytes.decodeToString() }
        val before = TerminalSessionBus.snapshot.value

        TerminalSessionBus.begin(profile)
        TerminalSessionBus.send("whoami\n")

        assertEquals("whoami\n", sent)
        assertEquals(before, TerminalSessionBus.snapshot.value)
        assertEquals(TerminalConnectionState.CONNECTED, TerminalSessionBus.snapshot.value.state)
    }
}
