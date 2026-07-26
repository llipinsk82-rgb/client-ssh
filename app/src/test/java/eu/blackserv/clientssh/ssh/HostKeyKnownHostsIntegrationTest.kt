package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HostKeyKnownHostsIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `accepted nonstandard-port host key survives new JSch instance`() {
        val knownHosts = temporaryFolder.newFile("known_hosts")
        val repositoryHost = "[example.test]:2222"
        val key = ed25519KeyBlob()
        val firstJsch = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        val decider = RecordingDecider()
        val repository = InteractiveHostKeyRepository(
            delegate = firstJsch.hostKeyRepository,
            displayHost = "example.test",
            port = 2222,
            decider = decider,
        )

        assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check(repositoryHost, key))
        val request = requireNotNull(decider.request)
        assertEquals("ssh-ed25519", request.algorithm)
        assertTrue(repository.persist(request.id))

        val secondJsch = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        assertEquals(HostKeyRepository.OK, secondJsch.hostKeyRepository.check(repositoryHost, key))
        val stored = knownHosts.readText()
        assertTrue(stored.contains(repositoryHost))
        assertTrue(stored.contains("ssh-ed25519"))
    }

    private fun ed25519KeyBlob(): ByteArray {
        val output = ByteArrayOutputStream()
        writeSshString(output, "ssh-ed25519".toByteArray(Charsets.US_ASCII))
        writeSshString(output, ByteArray(32) { index -> (index + 1).toByte() })
        return output.toByteArray()
    }

    private fun writeSshString(output: ByteArrayOutputStream, value: ByteArray) {
        output.write((value.size ushr 24) and 0xff)
        output.write((value.size ushr 16) and 0xff)
        output.write((value.size ushr 8) and 0xff)
        output.write(value.size and 0xff)
        output.write(value)
    }

    private class RecordingDecider : HostKeyTrustDecider {
        var request: HostKeyTrustRequest? = null

        override fun publishUnknown(request: HostKeyTrustRequest): Boolean {
            this.request = request
            return true
        }

        override fun reportChanged(request: HostKeyTrustRequest) = Unit
    }
}
