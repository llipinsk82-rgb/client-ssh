package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyTrustTest {
    @After
    fun cleanupBus() {
        HostKeyTrustBus.cancelPending()
    }

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
    fun `unknown key is rejected in first handshake then persisted after trust`() {
        val delegate = FakeRepository(initial = HostKeyRepository.NOT_INCLUDED)
        val decider = RecordingDecider(publish = true)
        val repository = InteractiveHostKeyRepository(
            delegate = delegate,
            displayHost = "example.test",
            port = 2222,
            decider = decider,
        )
        val key = keyBlob("ssh-ed25519")

        assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("[example.test]:2222", key))
        assertFalse(delegate.addCalled)
        val request = requireNotNull(decider.lastRequest)
        assertEquals("example.test", request.host)
        assertEquals(2222, request.port)
        assertEquals("ssh-ed25519", request.algorithm)
        assertEquals(HostKeyTrustKind.UNKNOWN, request.kind)

        assertTrue(repository.persist(request.id))
        assertTrue(delegate.addCalled)
        assertEquals(HostKeyRepository.OK, repository.check("[example.test]:2222", key))
    }

    @Test
    fun `unknown rejected key is never persisted`() {
        val delegate = FakeRepository(initial = HostKeyRepository.NOT_INCLUDED)
        val decider = RecordingDecider(publish = true)
        val repository = InteractiveHostKeyRepository(
            delegate = delegate,
            displayHost = "example.test",
            port = 22,
            decider = decider,
        )

        assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("example.test", keyBlob("ssh-rsa")))
        repository.discard(requireNotNull(decider.lastRequest).id)
        assertFalse(delegate.addCalled)
        assertFalse(repository.persist(decider.lastRequest!!.id))
    }

    @Test
    fun `unpublished concurrent unknown request is discarded`() {
        val delegate = FakeRepository(initial = HostKeyRepository.NOT_INCLUDED)
        val repository = InteractiveHostKeyRepository(
            delegate = delegate,
            displayHost = "example.test",
            port = 22,
            decider = RecordingDecider(publish = false),
        )

        assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("example.test", keyBlob("ssh-ed25519")))
        assertFalse(repository.persist(1L))
    }

    @Test
    fun `changed key is reported but can never be accepted`() {
        val delegate = FakeRepository(initial = HostKeyRepository.CHANGED)
        val decider = RecordingDecider(publish = true)
        val repository = InteractiveHostKeyRepository(
            delegate = delegate,
            displayHost = "example.test",
            port = 22,
            decider = decider,
        )

        assertEquals(HostKeyRepository.CHANGED, repository.check("example.test", keyBlob("ecdsa-sha2-nistp256")))
        assertFalse(delegate.addCalled)
        assertEquals(HostKeyTrustKind.CHANGED, decider.changedRequest?.kind)
        assertFalse(repository.persist(decider.changedRequest!!.id))
    }

    @Test
    fun `already known key does not prompt`() {
        val decider = RecordingDecider(publish = true)
        val repository = InteractiveHostKeyRepository(
            delegate = FakeRepository(initial = HostKeyRepository.OK),
            displayHost = "example.test",
            port = 22,
            decider = decider,
        )

        assertEquals(HostKeyRepository.OK, repository.check("example.test", keyBlob("ssh-ed25519")))
        assertEquals(null, decider.lastRequest)
    }

    @Test
    fun `bus decision suspends outside handshake and completes on trust`() = runBlocking {
        val request = HostKeyTrustBus.newRequest(
            host = "example.test",
            port = 22,
            algorithm = "ssh-ed25519",
            fingerprintSha256 = "SHA256:test",
            kind = HostKeyTrustKind.UNKNOWN,
        )
        assertTrue(HostKeyTrustBus.publishUnknown(request))

        val result = async { HostKeyTrustBus.awaitDecision(request.id, 2_000) }
        delay(10)
        HostKeyTrustBus.trust(request.id)

        assertTrue(result.await())
        assertEquals(null, HostKeyTrustBus.request.value)
    }

    @Test
    fun `bus rejection completes decision as false`() = runBlocking {
        val request = HostKeyTrustBus.newRequest(
            host = "example.test",
            port = 2222,
            algorithm = "ssh-rsa",
            fingerprintSha256 = "SHA256:test",
            kind = HostKeyTrustKind.UNKNOWN,
        )
        assertTrue(HostKeyTrustBus.publishUnknown(request))

        val result = async { HostKeyTrustBus.awaitDecision(request.id, 2_000) }
        delay(10)
        HostKeyTrustBus.reject(request.id)

        assertFalse(result.await())
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

    private class RecordingDecider(private val publish: Boolean) : HostKeyTrustDecider {
        var lastRequest: HostKeyTrustRequest? = null
        var changedRequest: HostKeyTrustRequest? = null

        override fun publishUnknown(request: HostKeyTrustRequest): Boolean {
            lastRequest = request
            return publish
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
