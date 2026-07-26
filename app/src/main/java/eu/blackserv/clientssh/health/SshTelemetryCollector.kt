package eu.blackserv.clientssh.health

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile

enum class SshTelemetryFailureKind {
    INTERACTIVE_AUTH_REQUIRED,
    HOST_KEY_NOT_TRUSTED,
    AUTHENTICATION_FAILED,
    CONNECT_TIMEOUT,
    NETWORK_UNAVAILABLE,
    COMMAND_TIMEOUT,
    COMMAND_FAILED,
    RESPONSE_INVALID,
    UNSUPPORTED_PROFILE,
    INTERNAL_ERROR,
}

sealed interface SshTelemetryCollectionResult {
    data class Success(val sample: SshTelemetrySample) : SshTelemetryCollectionResult

    data class Failure(
        val kind: SshTelemetryFailureKind,
        val message: String,
    ) : SshTelemetryCollectionResult
}

data class SshTelemetryExecResult(
    val stdout: String,
    val exitStatus: Int,
)

class SshTelemetryTransportException(
    val kind: SshTelemetryFailureKind,
    message: String? = null,
) : Exception(message)

fun interface SshTelemetryTransport {
    fun execute(
        profile: HostProfile,
        command: String,
        timeoutMs: Int,
        maxOutputBytes: Int,
    ): SshTelemetryExecResult
}

class SshTelemetryCollector(
    private val transport: SshTelemetryTransport,
) {
    fun collect(
        profile: HostProfile,
        pingTarget: TelemetryPingTarget? = TelemetryPingTarget.DEFAULT,
    ): SshTelemetryCollectionResult {
        if (profile.protocol != ConnectionProtocol.SSH) {
            return failure(
                SshTelemetryFailureKind.UNSUPPORTED_PROFILE,
                "Telemetria zasobów wymaga profilu SSH.",
            )
        }
        if (profile.authenticationMethod == AuthenticationMethod.INTERACTIVE) {
            return failure(
                SshTelemetryFailureKind.INTERACTIVE_AUTH_REQUIRED,
                "Profil interaktywny wymaga udziału użytkownika i nie może działać w tle.",
            )
        }

        return try {
            val result = transport.execute(
                profile = profile,
                command = buildSshTelemetryCommand(pingTarget),
                timeoutMs = COMMAND_TIMEOUT_MS,
                maxOutputBytes = SshTelemetryParser.MAX_PAYLOAD_BYTES,
            )
            if (result.exitStatus != 0) {
                failure(
                    SshTelemetryFailureKind.COMMAND_FAILED,
                    safeFailureMessage(SshTelemetryFailureKind.COMMAND_FAILED),
                )
            } else {
                runCatching { SshTelemetryParser.parse(result.stdout) }
                    .fold(
                        onSuccess = SshTelemetryCollectionResult::Success,
                        onFailure = {
                            failure(
                                SshTelemetryFailureKind.RESPONSE_INVALID,
                                safeFailureMessage(SshTelemetryFailureKind.RESPONSE_INVALID),
                            )
                        },
                    )
            }
        } catch (error: SshTelemetryTransportException) {
            failure(error.kind, safeFailureMessage(error.kind))
        } catch (_: Throwable) {
            failure(
                SshTelemetryFailureKind.INTERNAL_ERROR,
                safeFailureMessage(SshTelemetryFailureKind.INTERNAL_ERROR),
            )
        }
    }

    private fun failure(kind: SshTelemetryFailureKind, message: String) =
        SshTelemetryCollectionResult.Failure(kind = kind, message = message)

    companion object {
        const val COMMAND_TIMEOUT_MS = 15_000

        fun safeFailureMessage(kind: SshTelemetryFailureKind): String = when (kind) {
            SshTelemetryFailureKind.INTERACTIVE_AUTH_REQUIRED ->
                "Profil wymaga ręcznego uwierzytelnienia."

            SshTelemetryFailureKind.HOST_KEY_NOT_TRUSTED ->
                "Klucz hosta SSH nie jest zaufany. Najpierw połącz się ręcznie i zweryfikuj fingerprint."

            SshTelemetryFailureKind.AUTHENTICATION_FAILED ->
                "Uwierzytelnienie SSH nie powiodło się."

            SshTelemetryFailureKind.CONNECT_TIMEOUT ->
                "Przekroczono czas nawiązania połączenia SSH."

            SshTelemetryFailureKind.NETWORK_UNAVAILABLE ->
                "Nie można połączyć się z hostem SSH."

            SshTelemetryFailureKind.COMMAND_TIMEOUT ->
                "Przekroczono czas wykonania telemetrii."

            SshTelemetryFailureKind.COMMAND_FAILED ->
                "Serwer nie wykonał poleceń telemetrycznych."

            SshTelemetryFailureKind.RESPONSE_INVALID ->
                "Serwer zwrócił nieprawidłowe dane telemetryczne."

            SshTelemetryFailureKind.UNSUPPORTED_PROFILE ->
                "Profil nie obsługuje telemetrii SSH."

            SshTelemetryFailureKind.INTERNAL_ERROR ->
                "Nie udało się zebrać telemetrii SSH."
        }
    }
}

@JvmInline
value class TelemetryPingTarget private constructor(val value: String) {
    companion object {
        val DEFAULT = TelemetryPingTarget("1.1.1.1")

        fun parse(raw: String): TelemetryPingTarget? {
            val value = raw.trim()
            if (value.length !in 1..253) return null
            if (value.toIpv4OctetsOrNull() != null) return TelemetryPingTarget(value)
            val labels = value.split('.')
            if (labels.any { label -> !label.isValidDnsLabel() }) return null
            return TelemetryPingTarget(value.lowercase())
        }
    }
}

private fun String.toIpv4OctetsOrNull(): List<Int>? {
    val parts = split('.')
    if (parts.size != 4) return null
    val values = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return null
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }
    return values
}

private fun String.isValidDnsLabel(): Boolean {
    if (length !in 1..63) return false
    if (!first().isLetterOrDigit() || !last().isLetterOrDigit()) return false
    return all { it.isLetterOrDigit() || it == '-' }
}

internal fun buildSshTelemetryCommand(pingTarget: TelemetryPingTarget?): String {
    val dollar = '$'
    val pingTargetValue = pingTarget?.value
    val pingBlock = if (pingTargetValue == null) {
        """
        ping_status='DISABLED'
        ping_ms=''
        """.trimIndent()
    } else {
        """
        ping_status='UNAVAILABLE'
        ping_ms=''
        if command -v ping >/dev/null 2>&1; then
            ping_status='FAILED'
            ping_ms="${dollar}(ping -n -c 1 -W 2 '$pingTargetValue' 2>/dev/null | sed -n 's/.*time[=<]\([0-9.]*\)[[:space:]]*ms.*/\1/p' | head -n 1)"
            if [ -n "${dollar}ping_ms" ]; then
                ping_status='OK'
            fi
        fi
        """.trimIndent()
    }

    return """
        set -eu
        export LC_ALL=C

        cpu_line() {
            awk '/^cpu / { print ${dollar}2, ${dollar}3, ${dollar}4, ${dollar}5, ${dollar}6, ${dollar}7, ${dollar}8, ${dollar}9; exit }' /proc/stat
        }

        network_line() {
            awk 'NR > 2 { gsub(":", " "); rx += ${dollar}2; tx += ${dollar}10 } END { printf "%.0f %.0f\n", rx, tx }' /proc/net/dev
        }

        cpu_a="${dollar}(cpu_line)"
        set -- ${dollar}(network_line)
        net_a_rx="${dollar}1"
        net_a_tx="${dollar}2"

        sleep 1

        cpu_b="${dollar}(cpu_line)"
        set -- ${dollar}(network_line)
        net_b_rx="${dollar}1"
        net_b_tx="${dollar}2"

        mem_total="${dollar}(awk '/^MemTotal:/ { print ${dollar}2; exit }' /proc/meminfo)"
        mem_available="${dollar}(awk '/^MemAvailable:/ { print ${dollar}2; exit }' /proc/meminfo)"
        set -- ${dollar}(awk '{ print ${dollar}1, ${dollar}2, ${dollar}3; exit }' /proc/loadavg)
        load_1="${dollar}1"
        load_5="${dollar}2"
        load_15="${dollar}3"
        set -- ${dollar}(df -Pk / | awk 'NR == 2 { gsub("%", "", ${dollar}5); print ${dollar}2, ${dollar}3, ${dollar}4, ${dollar}5; exit }')
        disk_total="${dollar}1"
        disk_used="${dollar}2"
        disk_available="${dollar}3"
        disk_percent="${dollar}4"
        uptime_seconds="${dollar}(awk '{ printf "%.0f\n", ${dollar}1; exit }' /proc/uptime)"

        $pingBlock

        printf '%s\n' '${SshTelemetryParser.FORMAT_VERSION}'
        printf 'CPU_A=%s\n' "${dollar}cpu_a"
        printf 'CPU_B=%s\n' "${dollar}cpu_b"
        printf 'MEM_TOTAL_KB=%s\n' "${dollar}mem_total"
        printf 'MEM_AVAILABLE_KB=%s\n' "${dollar}mem_available"
        printf 'LOAD_1=%s\n' "${dollar}load_1"
        printf 'LOAD_5=%s\n' "${dollar}load_5"
        printf 'LOAD_15=%s\n' "${dollar}load_15"
        printf 'DISK_TOTAL_KB=%s\n' "${dollar}disk_total"
        printf 'DISK_USED_KB=%s\n' "${dollar}disk_used"
        printf 'DISK_AVAILABLE_KB=%s\n' "${dollar}disk_available"
        printf 'DISK_USED_PERCENT=%s\n' "${dollar}disk_percent"
        printf 'UPTIME_SECONDS=%s\n' "${dollar}uptime_seconds"
        printf 'NET_A_RX_BYTES=%s\n' "${dollar}net_a_rx"
        printf 'NET_A_TX_BYTES=%s\n' "${dollar}net_a_tx"
        printf 'NET_B_RX_BYTES=%s\n' "${dollar}net_b_rx"
        printf 'NET_B_TX_BYTES=%s\n' "${dollar}net_b_tx"
        printf 'SAMPLE_MS=1000\n'
        printf 'PING_STATUS=%s\n' "${dollar}ping_status"
        if [ "${dollar}ping_status" = 'OK' ]; then
            printf 'PING_MS=%s\n' "${dollar}ping_ms"
        fi
    """.trimIndent()
}
