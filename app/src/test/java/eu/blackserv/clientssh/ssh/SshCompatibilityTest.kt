package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.JSch
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.SshCompatibilityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshCompatibilityTest {
    @Test
    fun `legacy mode enables old Dropbear algorithms on selected session`() {
        val session = JSch().getSession("root", "receiver.local", 22)
        session.applyProfileSshCompatibility(profile(SshCompatibilityMode.LEGACY_ENIGMA2))

        assertEquals("ssh-rsa", session.getConfig("server_host_key").split(',').first())
        assertEquals("ssh-rsa", session.getConfig("PubkeyAcceptedAlgorithms").split(',').first())
        assertContains(session.getConfig("kex"), "diffie-hellman-group1-sha1")
        assertContains(session.getConfig("cipher.c2s"), "3des-cbc")
        assertContains(session.getConfig("cipher.s2c"), "aes128-cbc")
        assertContains(session.getConfig("mac.c2s"), "hmac-sha1")
        assertContains(session.getConfig("mac.s2c"), "hmac-md5")
    }

    @Test
    fun `modern profile does not prepend legacy host key algorithms per session`() {
        val session = JSch().getSession("root", "server.local", 22)
        val before = session.getConfig("server_host_key")

        session.applyProfileSshCompatibility(profile(SshCompatibilityMode.MODERN))

        assertEquals(before, session.getConfig("server_host_key"))
        assertFalse(profile(SshCompatibilityMode.MODERN).requiresLegacySshCompatibility())
        assertTrue(profile(SshCompatibilityMode.LEGACY_ENIGMA2).requiresLegacySshCompatibility())
    }

    private fun assertContains(raw: String, expected: String) {
        assertTrue("Expected $expected in $raw", raw.split(',').contains(expected))
    }

    private fun profile(mode: SshCompatibilityMode) = HostProfile(
        name = "Vu+ Zero",
        host = "receiver.local",
        port = 22,
        username = "root",
        protocol = ConnectionProtocol.SSH,
        authenticationMethod = AuthenticationMethod.PASSWORD,
        password = "secret",
        sshCompatibilityMode = mode,
    )
}
