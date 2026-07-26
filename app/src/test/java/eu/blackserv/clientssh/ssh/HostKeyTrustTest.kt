package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyTrustTest {
    @Test
    fun `parses algorithm from SSH key blob`() {
        val key = keyBlob("ssh-ed25519")
        assertEquals("ssh-ed25519", sshHostKeyAlgorithm(key))
    }

    @Test
    fun `malformed algorithm is rejected`() {
        assertEquals("unknown", sshHostKeyAlgorithm(byteArrayOf(0, 0, 0, 80, 1)))
    }

    @Test
    fun `fingerprint uses standard SHA256 base64 without padding`() {
        assertEquals(
            "SHA256:ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0",
            sshHostKeyFingerprintSha256("abc".toByteArray()),
        )
    }

    @Test
    fun `unknown trusted key is persisted and accepted`() {
        val delegate = FakeRepository(initial = HostKeyRepository.NOT_INCLUDED)
        val decider = RecordingDecider(trust = true)
        val repository = InteractiveHostKeyRepository(
            delegate = delegate,
            displayHost = "example.test",
            port = 2222,
            decider = decider,
            timeoutMillis = 1_000,
        )

        assertEquals(HostKeyRepository.OK, repository.check("[example.test]:2222", keyBlob("ssh-ed25519")))
        assertTrue(delegate.addCalled)
        assertEquals("example.test", decider.lastRequest?.host)
        assertEquals(2222, decider.lastRequest?.port)
        assertEquals("ssh-ed25519", decider.lastRequest?.algorithm)
        assertEquals(HostKeyTrustKind.UNKNOWN, decider.lastRequest?.kind)
    }

    @Test
    fun `unknown rejected key is not persisted`() {
        val delegate = FakeRepository(initial = HostKeyRepository.NOT_INCLUDED)
        val repository = InteractiveHostKeyRepository(
            delegate = delegate,
            displayHost = "example.test",
            port = 22,
            decider = RecordingDecider(trust = false),
            timeoutMillis = 1_000,
        )

        assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("example.test", keyBlob("ssh-rsa")))
        assertFalse(delegate.addCalled)
    }

    @Test
    fun `changed key is reported but can never be accepted`() {
        val delegate = FakeRepository(initial = HostKeyRepository.CHANGED)
        val decider = RecordingDecider(trust = true)
        val repository = InteractiveHostKeyRepository(
            delegate = delegate,
            displayHost = "example.test",
            port = 22,
            decider = decider,
            timeoutMillis = 1_000,
        )

        assertEquals(HostKeyRepository.CHANGED, repository.check("example.test", keyBlob("ecdsa-sha2-nistp256")))
        assertFalse(delegate.addCalled)
        assertEquals(HostKeyTrustKind.CHANGED, decider.changedRequest?.kind)
    }

    @Test
    fun `already known key does not prompt`() {
        val decider = RecordingDecider(trust = false)
        val repository = InteractiveHostKeyRepository(
            delegate = FakeRepository(initial = HostKeyRepository.OK),
            displayHost = "example.test",
            port = 22,
            decider = decider,
            timeoutMillis = 1_000,
        )

        assertEquals(HostKeyRepository.OK, repository.check("example.test", keyBlob("ssh-ed25519")))
        assertEquals(null, decider.lastRequest)
    }

    private fun keyBlob(algorithm: String): ByteArray {
        val name = algorithm.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(
            0,
            0,
            0,
            name.size.toByte(),
        ) + name + byteArrayOf(1, 2, 3, 4)
    }

    private class RecordingDecider(private val trust: Boolean) : HostKeyTrustDecider {
        var lastRequest: HostKeyTrustRequest? = null
        var changedRequest: HostKeyTrustRequest? = null

        override fun awaitTrust(request: HostKeyTrustRequest, timeoutMillis: Long): Boolean {
            lastRequest = request
            return trust
        }

        override fun reportChanged(request: HostKeyTrustRequest) {
            changedRequest = request
        }
    }

    private class FakeRepository(private val initial: Int) : HostKeyRepository {
        var addCalled = false

        override fun check(host: String, key: ByteArray): Int =
            if (addCalled) HostKeyRepository.OK else initial

        override fun add(hostkey: HostKey, userinfo: UserInfo?) {
            addCalled = true
        }

        override fun remove(host: String, type: String?) = Unit

        override fun remove(host: String, type: String?, key: ByteArray?) = Unit

        override fun getKnownHostsRepositoryID(): String = "fake"

        override fun getHostKey(): Array<HostKey> = emptyArray()

        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }
}
