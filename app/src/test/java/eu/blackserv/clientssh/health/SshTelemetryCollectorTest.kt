package eu.blackserv.clientssh.health

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshTelemetryCollectorTest {
    @Test
    fun `collector executes fixed command and parses sample`() {
        var capturedCommand = ""
        val collector = SshTelemetryCollector(
            SshTelemetryTransport { _, command, timeoutMs, maxOutputBytes ->
                capturedCommand = command
                assertEquals(SshTelemetryCollector.COMMAND_TIMEOUT_MS, timeoutMs)
                assertEquals(SshTelemetryParser.MAX_PAYLOAD_BYTES, maxOutputBytes)
                SshTelemetryExecResult(validPayload(), 0)
            },
        )

        val result = collector.collect(passwordProfile())

        assertTrue(result is SshTelemetryCollectionResult.Success)
        assertTrue(capturedCommand.contains("/proc/stat"))
        assertTrue(capturedCommand.contains("/proc/meminfo"))
        assertTrue(capturedCommand.contains("/proc/net/dev"))
        assertTrue(capturedCommand.contains("df -Pk /"))
        assertFalse(capturedCommand.contains(passwordProfile().password))
    }

    @Test
    fun `telemetry command is safe for old BusyBox and old procfs`() {
        val command = buildSshTelemetryCommand(TelemetryPingTarget.DEFAULT)

        assertFalse(command.contains("set -eu"))
        assertFalse(command.contains("set -e"))
        assertTrue(command.contains("/^MemAvailable:/"))
        assertTrue(command.contains("free_mem + buffers + cached + reclaimable - shmem"))
        assertTrue(command.contains("df -Pk /"))
        assertTrue(command.contains("df -k /"))
        assertTrue(command.contains("\${steal:-0}"))
        assertTrue(command.contains("exit 0"))
    }

    @Test
    fun `interactive profile is rejected without calling transport`() {
        var calls = 0
        val collector = SshTelemetryCollector(
            SshTelemetryTransport { _, _, _, _ ->
                calls++
                SshTelemetryExecResult(validPayload(), 0)
            },
        )
        val profile = passwordProfile().copy(authenticationMethod = AuthenticationMethod.INTERACTIVE)

        val result = collector.collect(profile)

        assertEquals(0, calls)
        assertEquals(
            SshTelemetryFailureKind.INTERACTIVE_AUTH_REQUIRED,
            (result as SshTelemetryCollectionResult.Failure).kind,
        )
    }

    @Test
    fun `transport details are never exposed`() {
        val marker = "transport-sensitive-marker"
        val collector = SshTelemetryCollector(
            SshTelemetryTransport { _, _, _, _ ->
                throw SshTelemetryTransportException(
                    SshTelemetryFailureKind.AUTHENTICATION_FAILED,
                    marker,
                )
            },
        )

        val result = collector.collect(passwordProfile()) as SshTelemetryCollectionResult.Failure

        assertEquals(SshTelemetryFailureKind.AUTHENTICATION_FAILED, result.kind)
        assertFalse(result.message.contains(marker))
    }

    @Test
    fun `invalid response is mapped without returning raw output`() {
        val marker = "raw-output-marker"
        val collector = SshTelemetryCollector(
            SshTelemetryTransport { _, _, _, _ -> SshTelemetryExecResult(marker, 0) },
        )

        val result = collector.collect(passwordProfile()) as SshTelemetryCollectionResult.Failure

        assertEquals(SshTelemetryFailureKind.RESPONSE_INVALID, result.kind)
        assertFalse(result.message.contains(marker))
    }

    @Test
    fun `nonzero exit status becomes command failure`() {
        val collector = SshTelemetryCollector(
            SshTelemetryTransport { _, _, _, _ -> SshTelemetryExecResult("", 12) },
        )

        val result = collector.collect(passwordProfile()) as SshTelemetryCollectionResult.Failure

        assertEquals(SshTelemetryFailureKind.COMMAND_FAILED, result.kind)
    }

    @Test
    fun `ping target accepts hostnames and IPv4 only`() {
        assertEquals("example.com", TelemetryPingTarget.parse("Example.COM")?.value)
        assertEquals("1.1.1.1", TelemetryPingTarget.parse("1.1.1.1")?.value)
        assertNull(TelemetryPingTarget.parse("1.1.1.1;id"))
        assertNull(TelemetryPingTarget.parse("-n"))
        assertNull(TelemetryPingTarget.parse("host name"))
        assertNull(TelemetryPingTarget.parse("256.1.1.1"))
    }

    @Test
    fun `disabled ping command contains no ping execution`() {
        val command = buildSshTelemetryCommand(null)

        assertTrue(command.contains("ping_status='DISABLED'"))
        assertFalse(command.contains("command -v ping"))
        assertFalse(command.contains("ping -n -c"))
    }

    private fun passwordProfile() = HostProfile(
        name = "node",
        host = "example.com",
        port = 22,
        username = "operator",
        protocol = ConnectionProtocol.SSH,
        authenticationMethod = AuthenticationMethod.PASSWORD,
        password = "sample-credential",
    )

    private fun validPayload(): String = """
        ${SshTelemetryParser.FORMAT_VERSION}
        CPU_A=100 0 100 700 0 0 0 0
        CPU_B=150 0 150 800 0 0 0 0
        MEM_TOTAL_KB=8000000
        MEM_AVAILABLE_KB=5000000
        LOAD_1=1.25
        LOAD_5=1.00
        LOAD_15=0.75
        DISK_TOTAL_KB=10000000
        DISK_USED_KB=4000000
        DISK_AVAILABLE_KB=6000000
        DISK_USED_PERCENT=40
        UPTIME_SECONDS=86400
        NET_A_RX_BYTES=100000
        NET_A_TX_BYTES=50000
        NET_B_RX_BYTES=102000
        NET_B_TX_BYTES=51000
        SAMPLE_MS=1000
        PING_STATUS=OK
        PING_MS=12.4
    """.trimIndent() + "\n"
}
