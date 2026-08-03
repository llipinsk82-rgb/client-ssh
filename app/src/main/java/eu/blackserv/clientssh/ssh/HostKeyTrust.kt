package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

enum class HostKeyTrustKind {
    UNKNOWN,
    CHANGED,
}

internal fun sshHostKeyAlias(host: String, port: Int): String {
    require(port in 1..65_535) { "Port SSH poza zakresem" }
    val trimmed = host.trim()
    require(trimmed.isNotBlank()) { "Host SSH jest pusty" }
    val normalized = if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
        trimmed.substring(1, trimmed.length - 1)
    } else {
        trimmed
    }
    return "[$normalized]:$port"
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
    fun publishUnknown(request: HostKeyTrustRequest): Boolean
    fun reportChanged(request: HostKeyTrustRequest)
}

object HostKeyTrustBus : HostKeyTrustDecider {
    private data class Pending(
        val request: HostKeyTrustRequest,
        val decision: CompletableDeferred<Boolean> = CompletableDeferred(),
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

    override fun publishUnknown(request: HostKeyTrustRequest): Boolean {
        require(request.kind == HostKeyTrustKind.UNKNOWN)
        synchronized(lock) {
            if (pending != null || changedAlertId != null) return false
            pending = Pending(request)
            _request.value = request
            return true
        }
    }

    override fun reportChanged(request: HostKeyTrustRequest) {
        require(request.kind == HostKeyTrustKind.CHANGED)
        synchronized(lock) {
            if (pending != null || changedAlertId != null) return
            changedAlertId = request.id
            _request.value = request
        }
    }

    fun pendingUnknownFor(host: String, port: Int): HostKeyTrustRequest? = synchronized(lock) {
        pending?.request?.takeIf { it.host == host && it.port == port }
    }

    suspend fun awaitDecision(id: Long, timeoutMillis: Long): Boolean {
        require(timeoutMillis in 1_000L..300_000L)
        val item = synchronized(lock) {
            pending?.takeIf { it.request.id == id }
        } ?: return false

        val result = withTimeoutOrNull(timeoutMillis) { item.decision.await() } ?: false
        synchronized(lock) {
            if (pending === item) {
                pending = null
                if (_request.value?.id == item.request.id) _request.value = null
            }
        }
        return result
    }

    fun trust(id: Long) = completeUnknown(id, trusted = true)

    fun reject(id: Long) = completeUnknown(id, trusted = false)

    fun dismiss(id: Long) {
        synchronized(lock) {
            if (changedAlertId == id) {
                changedAlertId = null
                if (_request.value?.id == id) _request.value = null
                return
            }
        }
        reject(id)
    }

    fun cancelUnknown() {
        val item = synchronized(lock) {
            val current = pending
            pending = null
            if (_request.value?.kind == HostKeyTrustKind.UNKNOWN) _request.value = null
            current
        }
        item?.decision?.complete(false)
    }

    fun cancelPending() {
        val item = synchronized(lock) {
            val current = pending
            pending = null
            changedAlertId = null
            _request.value = null
            current
        }
        item?.decision?.complete(false)
    }

    private fun completeUnknown(id: Long, trusted: Boolean) {
        val item = synchronized(lock) {
            pending?.takeIf { it.request.id == id }?.also {
                if (_request.value?.id == id) _request.value = null
            }
        } ?: return
        item.decision.complete(trusted)
    }
}

class PortScopedHostKeyRepository(
    private val delegate: HostKeyRepository,
    private val displayHost: String,
    private val port: Int,
) : HostKeyRepository {
    private val endpointAlias: String
        get() = sshHostKeyAlias(displayHost, port)

    override fun check(repositoryHost: String, key: ByteArray): Int =
        delegate.check(endpointAlias, key)

    override fun add(hostkey: HostKey, userinfo: UserInfo?) = delegate.add(hostkey, userinfo)

    override fun remove(host: String, type: String?) = delegate.remove(endpointAlias, type)

    override fun remove(host: String, type: String?, key: ByteArray?) =
        delegate.remove(endpointAlias, type, key)

    override fun getKnownHostsRepositoryID(): String = delegate.knownHostsRepositoryID

    override fun getHostKey(): Array<HostKey> = delegate.hostKey

    override fun getHostKey(host: String?, type: String?): Array<HostKey> =
        delegate.getHostKey(if (host == null) null else endpointAlias, type)
}

class InteractiveHostKeyRepository(
    private val delegate: HostKeyRepository,
    private val displayHost: String,
    private val port: Int,
    private val decider: HostKeyTrustDecider = HostKeyTrustBus,
) : HostKeyRepository {
    private data class PendingKey(
        val requestId: Long,
        val repositoryHost: String,
        val key: ByteArray,
    )

    private val lock = Any()
    private var pendingKey: PendingKey? = null

    override fun check(repositoryHost: String, key: ByteArray): Int {
        val endpointHost = sshHostKeyAlias(displayHost, port)
        val existing = delegate.check(endpointHost, key)
        if (existing == HostKeyRepository.OK) return existing

        val kind = if (existing == HostKeyRepository.CHANGED) {
            HostKeyTrustKind.CHANGED
        } else {
            HostKeyTrustKind.UNKNOWN
        }
        val request = if (decider === HostKeyTrustBus) {
            HostKeyTrustBus.newRequest(
                host = displayHost,
                port = port,
                algorithm = sshHostKeyAlgorithm(key),
                fingerprintSha256 = sshHostKeyFingerprintSha256(key),
                kind = kind,
            )
        } else {
            HostKeyTrustRequest(
                id = 1L,
                host = displayHost,
                port = port,
                algorithm = sshHostKeyAlgorithm(key),
                fingerprintSha256 = sshHostKeyFingerprintSha256(key),
                kind = kind,
            )
        }

        if (existing == HostKeyRepository.CHANGED) {
            decider.reportChanged(request)
            return HostKeyRepository.CHANGED
        }

        val candidate = PendingKey(request.id, endpointHost, key.copyOf())
        synchronized(lock) {
            pendingKey?.key?.fill(0)
            pendingKey = candidate
        }
        if (!decider.publishUnknown(request)) {
            discard(request.id)
        }
        return HostKeyRepository.NOT_INCLUDED
    }

    fun persist(requestId: Long): Boolean {
        val item = synchronized(lock) {
            pendingKey?.takeIf { it.requestId == requestId }?.also { pendingKey = null }
        } ?: return false

        val storedKey = item.key.copyOf()
        return try {
            delegate.add(HostKey(item.repositoryHost, storedKey), null)
            delegate.check(item.repositoryHost, item.key) == HostKeyRepository.OK
        } catch (_: Throwable) {
            storedKey.fill(0)
            false
        } finally {
            item.key.fill(0)
        }
    }

    fun discard(requestId: Long) {
        val item = synchronized(lock) {
            pendingKey?.takeIf { it.requestId == requestId }?.also { pendingKey = null }
        }
        item?.key?.fill(0)
    }

    fun discardAll() {
        val item = synchronized(lock) {
            val current = pendingKey
            pendingKey = null
            current
        }
        item?.key?.fill(0)
    }

    override fun add(hostkey: HostKey, userinfo: UserInfo?) = delegate.add(hostkey, userinfo)

    override fun remove(host: String, type: String?) = delegate.remove(host, type)

    override fun remove(host: String, type: String?, key: ByteArray?) = delegate.remove(host, type, key)

    override fun getKnownHostsRepositoryID(): String = delegate.knownHostsRepositoryID

    override fun getHostKey(): Array<HostKey> = delegate.hostKey

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = delegate.getHostKey(host, type)
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
