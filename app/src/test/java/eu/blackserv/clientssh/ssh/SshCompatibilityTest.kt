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
    fun `automatic compatibility keeps modern algorithms first and adds fallbacks`() {
        val session = JSch().getSession("root", "receiver.local", 22)
        val modernHostKeyFirst = session.getConfig("server_host_key").split(',').first()
        val modernPublicKeyFirst = session.getConfig("PubkeyAcceptedAlgorithms").split(',').first()

        session.applyProfileSshCompatibility(profile(SshCompatibilityMode.LEGACY_ENIGMA2))

        assertEquals(modernHostKeyFirst, session.getConfig("server_host_key").split(',').first())
        assertEquals(modernPublicKeyFirst, session.getConfig("PubkeyAcceptedAlgorithms").split(',').first())
        assertContains(session.getConfig("server_host_key"), "ssh-rsa")
        assertContains(session.getConfig("PubkeyAcceptedAlgorithms"), "ssh-rsa")
        assertContains(session.getConfig("kex"), "diffie-hellman-group1-sha1")
        assertContains(session.getConfig("cipher.c2s"), "3des-cbc")
        assertContains(session.getConfig("cipher.s2c"), "aes128-cbc")
        assertContains(session.getConfig("mac.c2s"), "hmac-sha1")
        assertContains(session.getConfig("mac.s2c"), "hmac-md5")
    }

    @Test
    fun `modern-only profile does not add compatibility algorithms per session`() {
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
        name = "Receiver",
        host = "receiver.local",
        port = 22,
        username = "root",
        protocol = ConnectionProtocol.SSH,
        authenticationMethod = AuthenticationMethod.PASSWORD,
        password = "secret",
        sshCompatibilityMode = mode,
    )
}
