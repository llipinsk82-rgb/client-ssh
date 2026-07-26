package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HostKeyTrustKind {
    UNKNOWN,
    CHANGED,
}

data class HostKeyTrustRequest(
    val id: Long,
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprintSha256: String,
    val kind: HostKeyTrustKind,
)

interface HostKeyTrustDecider {
    fun awaitTrust(request: HostKeyTrustRequest, timeoutMillis: Long): Boolean
    fun reportChanged(request: HostKeyTrustRequest)
}

object HostKeyTrustBus : HostKeyTrustDecider {
    private data class Pending(
        val request: HostKeyTrustRequest,
        val latch: CountDownLatch = CountDownLatch(1),
        var trusted: Boolean = false,
    )

    private val lock = Any()
    private val nextId = AtomicLong(1L)
    private var pending: Pending? = null
    private var changedAlertId: Long? = null
    private val _request = MutableStateFlow<HostKeyTrustRequest?>(null)
    val request = _request.asStateFlow()

    fun newRequest(
        host: String,
        port: Int,
        algorithm: String,
        fingerprintSha256: String,
        kind: HostKeyTrustKind,
    ): HostKeyTrustRequest = HostKeyTrustRequest(
        id = nextId.getAndIncrement(),
        host = host,
        port = port,
        algorithm = algorithm,
        fingerprintSha256 = fingerprintSha256,
        kind = kind,
    )

    override fun awaitTrust(request: HostKeyTrustRequest, timeoutMillis: Long): Boolean {
        require(request.kind == HostKeyTrustKind.UNKNOWN)
        require(timeoutMillis in 1_000L..300_000L)
        val item = Pending(request)
        synchronized(lock) {
            if (pending != null || changedAlertId != null) return false
            pending = item
            _request.value = request
        }

        val completed = try {
            item.latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        synchronized(lock) {
            if (pending === item) {
                pending = null
                _request.value = null
            }
        }
        return completed && item.trusted
    }

    override fun reportChanged(request: HostKeyTrustRequest) {
        require(request.kind == HostKeyTrustKind.CHANGED)
        synchronized(lock) {
            if (pending != null || changedAlertId != null) return
            changedAlertId = request.id
            _request.value = request
        }
    }

    fun trust(id: Long) = respond(id, trusted = true)

    fun reject(id: Long) = respond(id, trusted = false)

    fun dismiss(id: Long) {
        synchronized(lock) {
            if (changedAlertId == id) {
                changedAlertId = null
                _request.value = null
            } else if (pending?.request?.id == id) {
                pending?.trusted = false
                pending?.latch?.countDown()
            }
        }
    }

    fun cancelPending() {
        synchronized(lock) {
            pending?.trusted = false
            pending?.latch?.countDown()
            pending = null
            changedAlertId = null
            _request.value = null
        }
    }

    private fun respond(id: Long, trusted: Boolean) {
        synchronized(lock) {
            val item = pending ?: return
            if (item.request.id != id) return
            item.trusted = trusted
            item.latch.countDown()
        }
    }
}

class InteractiveHostKeyRepository(
    private val delegate: HostKeyRepository,
    private val displayHost: String,
    private val port: Int,
    private val decider: HostKeyTrustDecider = HostKeyTrustBus,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : HostKeyRepository {
    override fun check(repositoryHost: String, key: ByteArray): Int {
        val existing = delegate.check(repositoryHost, key)
        if (existing == HostKeyRepository.OK) return existing

        val kind = if (existing == HostKeyRepository.CHANGED) {
            HostKeyTrustKind.CHANGED
        } else {
            HostKeyTrustKind.UNKNOWN
        }
        val algorithm = sshHostKeyAlgorithm(key)
        val fingerprint = sshHostKeyFingerprintSha256(key)
        val request = if (decider === HostKeyTrustBus) {
            HostKeyTrustBus.newRequest(displayHost, port, algorithm, fingerprint, kind)
        } else {
            HostKeyTrustRequest(1L, displayHost, port, algorithm, fingerprint, kind)
        }

        if (existing == HostKeyRepository.CHANGED) {
            decider.reportChanged(request)
            return HostKeyRepository.CHANGED
        }

        if (!decider.awaitTrust(request, timeoutMillis)) return HostKeyRepository.NOT_INCLUDED

        delegate.add(HostKey(repositoryHost, key.copyOf()), null)
        return delegate.check(repositoryHost, key)
    }

    override fun add(hostkey: HostKey, userinfo: UserInfo?) = delegate.add(hostkey, userinfo)

    override fun remove(host: String, type: String?) = delegate.remove(host, type)

    override fun remove(host: String, type: String?, key: ByteArray?) = delegate.remove(host, type, key)

    override fun getKnownHostsRepositoryID(): String = delegate.knownHostsRepositoryID

    override fun getHostKey(): Array<HostKey> = delegate.hostKey

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = delegate.getHostKey(host, type)

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    }
}

internal fun sshHostKeyFingerprintSha256(key: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}

internal fun sshHostKeyAlgorithm(key: ByteArray): String {
    if (key.size < 5) return "unknown"
    val length = ((key[0].toInt() and 0xff) shl 24) or
        ((key[1].toInt() and 0xff) shl 16) or
        ((key[2].toInt() and 0xff) shl 8) or
        (key[3].toInt() and 0xff)
    if (length !in 1..64 || 4 + length > key.size) return "unknown"
    val value = key.copyOfRange(4, 4 + length)
    return try {
        if (value.any { byte -> byte.toInt() !in 0x21..0x7e }) "unknown"
        else value.toString(Charsets.US_ASCII)
    } finally {
        value.fill(0)
    }
}
