package eu.blackserv.clientssh.health

import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

data class HealthTarget(
    val host: String,
    val port: Int,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65_535) { "port must be between 1 and 65535" }
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            "timeoutMs must be between $MIN_TIMEOUT_MS and $MAX_TIMEOUT_MS"
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000
        const val MIN_TIMEOUT_MS = 250
        const val MAX_TIMEOUT_MS = 30_000
    }
}

fun interface HealthProbe {
    fun check(target: HealthTarget): HealthObservation
}

fun interface TcpConnector {
    fun connect(target: HealthTarget): Long
}

class TcpHealthProbe(
    private val connector: TcpConnector = JavaSocketConnector,
) : HealthProbe {
    override fun check(target: HealthTarget): HealthObservation =
        runCatching { connector.connect(target) }
            .fold(
                onSuccess = { responseTimeMs ->
                    HealthObservation.Success(responseTimeMs.coerceAtLeast(0L))
                },
                onFailure = { error ->
                    HealthObservation.Failure(error.toSafeHealthMessage())
                },
            )
}

private object JavaSocketConnector : TcpConnector {
    override fun connect(target: HealthTarget): Long = measureTimeMillis {
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(target.host.trim(), target.port),
                target.timeoutMs,
            )
        }
    }
}

private fun Throwable.toSafeHealthMessage(): String {
    val type = this::class.simpleName ?: "ConnectionError"
    val detail = message
        ?.replace(Regex("[\\r\\n]+"), " ")
        ?.trim()
        ?.take(160)
        .orEmpty()
    return if (detail.isBlank()) type else "$type: $detail"
}
