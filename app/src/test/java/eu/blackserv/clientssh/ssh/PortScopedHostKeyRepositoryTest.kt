package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PortScopedHostKeyRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `same hostname on different ports has independent host keys`() {
        val knownHosts = temporaryFolder.newFile("known_hosts")
        val firstKey = ed25519KeyBlob(1)
        val secondKey = ed25519KeyBlob(101)
        val writer = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        writer.hostKeyRepository.add(HostKey(sshHostKeyAlias("blackserv.eu", 3377), firstKey), null)
        writer.hostKeyRepository.add(HostKey(sshHostKeyAlias("blackserv.eu", 3388), secondKey), null)

        val reader = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        val first = PortScopedHostKeyRepository(reader.hostKeyRepository, "blackserv.eu", 3377)
        val second = PortScopedHostKeyRepository(reader.hostKeyRepository, "blackserv.eu", 3388)

        assertEquals(HostKeyRepository.OK, first.check("blackserv.eu", firstKey))
        assertEquals(HostKeyRepository.CHANGED, first.check("blackserv.eu", secondKey))
        assertEquals(HostKeyRepository.OK, second.check("blackserv.eu", secondKey))
        assertEquals(HostKeyRepository.CHANGED, second.check("blackserv.eu", firstKey))
    }

    @Test
    fun `endpoint aliases include port and normalize brackets`() {
        assertEquals("[blackserv.eu]:22", sshHostKeyAlias("blackserv.eu", 22))
        assertEquals("[blackserv.eu]:3377", sshHostKeyAlias("[blackserv.eu]", 3377))
        assertNotEquals(sshHostKeyAlias("blackserv.eu", 3377), sshHostKeyAlias("blackserv.eu", 3388))
    }

    private fun ed25519KeyBlob(seed: Int): ByteArray {
        val output = ByteArrayOutputStream()
        writeSshString(output, "ssh-ed25519".toByteArray(Charsets.US_ASCII))
        writeSshString(output, ByteArray(32) { index -> (seed + index).toByte() })
        return output.toByteArray()
    }

    private fun writeSshString(output: ByteArrayOutputStream, value: ByteArray) {
        output.write((value.size ushr 24) and 0xff)
        output.write((value.size ushr 16) and 0xff)
        output.write((value.size ushr 8) and 0xff)
        output.write(value.size and 0xff)
        output.write(value)
    }
}
