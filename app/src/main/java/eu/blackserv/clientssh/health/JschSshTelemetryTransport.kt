package eu.blackserv.clientssh.health

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.ssh.PortScopedHostKeyRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.min

class JschSshTelemetryTransport(context: Context) : SshTelemetryTransport {
    private val appContext = context.applicationContext

    override fun execute(
        profile: HostProfile,
        command: String,
        timeoutMs: Int,
        maxOutputBytes: Int,
    ): SshTelemetryExecResult {
        require(timeoutMs in 1_000..60_000) { "timeoutMs poza zakresem" }
        require(maxOutputBytes in 1_024..SshTelemetryParser.MAX_PAYLOAD_BYTES) {
            "maxOutputBytes poza zakresem"
        }
        if (profile.host.isBlank() || profile.username.isBlank()) {
            throw SshTelemetryTransportException(SshTelemetryFailureKind.UNSUPPORTED_PROFILE)
        }

        val knownHosts = File(appContext.filesDir, KNOWN_HOSTS_PATH)
        if (!knownHosts.isFile || knownHosts.length() == 0L) {
            throw SshTelemetryTransportException(SshTelemetryFailureKind.HOST_KEY_NOT_TRUSTED)
        }

        val deadlineNanos = System.nanoTime() + timeoutMs * NANOS_PER_MILLISECOND
        val jsch = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        var session: Session? = null

        try {
            configureIdentity(jsch, profile)
            session = jsch.getSession(
                profile.username.trim(),
                profile.host.trim(),
                profile.port,
            ).apply {
                hostKeyRepository = PortScopedHostKeyRepository(
                    delegate = jsch.hostKeyRepository,
                    displayHost = profile.host.trim(),
                    port = profile.port,
                )
                if (profile.authenticationMethod == AuthenticationMethod.PASSWORD) {
                    setPassword(profile.password)
                }
                setConfig("StrictHostKeyChecking", "yes")
                setConfig(
                    "PreferredAuthentications",
                    when (profile.authenticationMethod) {
                        AuthenticationMethod.PASSWORD -> "password,keyboard-interactive"
                        AuthenticationMethod.PRIVATE_KEY -> "publickey"
                        AuthenticationMethod.INTERACTIVE -> throw SshTelemetryTransportException(
                            SshTelemetryFailureKind.INTERACTIVE_AUTH_REQUIRED,
                        )
                    },
                )
                setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)
                setServerAliveCountMax(SERVER_ALIVE_MAX_MISSES)
            }
            session.connect(min(CONNECT_TIMEOUT_MS, remainingMillis(deadlineNanos)))
            return executeCommand(
                session = session,
                command = command,
                timeoutMs = remainingMillis(deadlineNanos),
                maxOutputBytes = maxOutputBytes,
            )
        } catch (error: SshTelemetryTransportException) {
            throw error
        } catch (error: JSchException) {
            throw classifyJschFailure(error)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw SshTelemetryTransportException(SshTelemetryFailureKind.COMMAND_TIMEOUT)
        } catch (_: Throwable) {
            throw SshTelemetryTransportException(SshTelemetryFailureKind.INTERNAL_ERROR)
        } finally {
            runCatching { session?.disconnect() }
            runCatching { jsch.removeAllIdentity() }
        }
    }

    private fun configureIdentity(jsch: JSch, profile: HostProfile) {
        if (profile.authenticationMethod != AuthenticationMethod.PRIVATE_KEY) return
        if (profile.privateKey.isBlank()) {
            throw SshTelemetryTransportException(SshTelemetryFailureKind.AUTHENTICATION_FAILED)
        }

        val privateKeyBytes = profile.privateKey.toByteArray(StandardCharsets.UTF_8)
        val passphraseBytes = profile.privateKeyPassphrase
            .takeIf(String::isNotEmpty)
            ?.toByteArray(StandardCharsets.UTF_8)
        try {
            jsch.addIdentity(profile.id, privateKeyBytes, null, passphraseBytes)
        } catch (_: JSchException) {
            throw SshTelemetryTransportException(SshTelemetryFailureKind.AUTHENTICATION_FAILED)
        } finally {
            privateKeyBytes.fill(0)
            passphraseBytes?.fill(0)
        }
    }

    private fun executeCommand(
        session: Session,
        command: String,
        timeoutMs: Int,
        maxOutputBytes: Int,
    ): SshTelemetryExecResult {
        val channel = session.openChannel("exec") as ChannelExec
        val output = ByteArrayOutputStream(min(maxOutputBytes, INITIAL_OUTPUT_CAPACITY))
        val buffer = ByteArray(READ_BUFFER_SIZE)
        var totalBytes = 0
        val deadlineNanos = System.nanoTime() + timeoutMs * NANOS_PER_MILLISECOND

        try {
            channel.setInputStream(null)
            channel.setCommand(command)
            val stdout = channel.inputStream
            val stderr = channel.errStream
            channel.connect(min(CHANNEL_CONNECT_TIMEOUT_MS, remainingMillis(deadlineNanos)))

            while (true) {
                totalBytes += drainAvailable(
                    stream = stdout,
                    buffer = buffer,
                    maxRemainingBytes = maxOutputBytes - totalBytes,
                    output = output,
                )
                totalBytes += drainAvailable(
                    stream = stderr,
                    buffer = buffer,
                    maxRemainingBytes = maxOutputBytes - totalBytes,
                    output = null,
                )

                if (channel.isClosed && stdout.available() == 0 && stderr.available() == 0) break
                if (System.nanoTime() >= deadlineNanos) {
                    throw SshTelemetryTransportException(SshTelemetryFailureKind.COMMAND_TIMEOUT)
                }
                Thread.sleep(POLL_INTERVAL_MS)
            }

            return SshTelemetryExecResult(
                stdout = output.toString(StandardCharsets.UTF_8.name()),
                exitStatus = channel.exitStatus,
            )
        } finally {
            buffer.fill(0)
            runCatching { channel.disconnect() }
        }
    }

    private fun drainAvailable(
        stream: InputStream,
        buffer: ByteArray,
        maxRemainingBytes: Int,
        output: ByteArrayOutputStream?,
    ): Int {
        var total = 0
        while (stream.available() > 0) {
            if (maxRemainingBytes - total <= 0) {
                throw SshTelemetryTransportException(SshTelemetryFailureKind.RESPONSE_INVALID)
            }
            val requested = min(buffer.size, maxRemainingBytes - total)
            val read = stream.read(buffer, 0, requested)
            if (read < 0) break
            if (read == 0) continue
            output?.write(buffer, 0, read)
            total += read
        }
        return total
    }

    private fun remainingMillis(deadlineNanos: Long): Int {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) {
            throw SshTelemetryTransportException(SshTelemetryFailureKind.COMMAND_TIMEOUT)
        }
        return (remainingNanos / NANOS_PER_MILLISECOND)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun classifyJschFailure(error: JSchException): SshTelemetryTransportException {
        val raw = error.message.orEmpty().lowercase()
        val kind = when {
            raw.contains("reject hostkey") ||
                raw.contains("unknownhostkey") ||
                raw.contains("host key has changed") -> SshTelemetryFailureKind.HOST_KEY_NOT_TRUSTED

            raw.contains("auth fail") ||
                raw.contains("authentication") ||
                raw.contains("invalid private") -> SshTelemetryFailureKind.AUTHENTICATION_FAILED

            raw.contains("timeout") -> SshTelemetryFailureKind.CONNECT_TIMEOUT

            raw.contains("unknownhost") ||
                raw.contains("connection refused") ||
                raw.contains("socket is not established") ||
                raw.contains("network is unreachable") -> SshTelemetryFailureKind.NETWORK_UNAVAILABLE

            else -> SshTelemetryFailureKind.INTERNAL_ERROR
        }
        return SshTelemetryTransportException(kind)
    }

    private companion object {
        const val KNOWN_HOSTS_PATH = "ssh/known_hosts"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val CHANNEL_CONNECT_TIMEOUT_MS = 5_000
        const val SERVER_ALIVE_INTERVAL_MS = 5_000
        const val SERVER_ALIVE_MAX_MISSES = 1
        const val POLL_INTERVAL_MS = 25L
        const val READ_BUFFER_SIZE = 1_024
        const val INITIAL_OUTPUT_CAPACITY = 4_096
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
