package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SshTelemetryParserTest {
    @Test
    fun `parses complete telemetry and calculates rates`() {
        val sample = SshTelemetryParser.parse(validPayload())

        assertEquals(50.0, sample.cpuUsagePercent, 0.001)
        assertEquals(8_000_000L, sample.memoryTotalKb)
        assertEquals(3_000_000L, sample.memoryUsedKb)
        assertEquals(37.5, sample.memoryUsedPercent, 0.001)
        assertEquals(1.25, sample.load1, 0.001)
        assertEquals(4_000_000L, sample.diskUsedKb)
        assertEquals(40, sample.diskUsedPercent)
        assertEquals(86_400L, sample.uptimeSeconds)
        assertEquals(2_000L, sample.networkRxBytesPerSecond)
        assertEquals(1_000L, sample.networkTxBytesPerSecond)
        assertEquals(TelemetryPingStatus.OK, sample.pingStatus)
        assertEquals(12.4, sample.pingMs!!, 0.001)
        assertEquals(SshTelemetryStatus.OK, sample.status)
    }

    @Test
    fun `accepts unavailable ping as partial telemetry`() {
        val sample = SshTelemetryParser.parse(
            validPayload()
                .replace("PING_STATUS=OK\n", "PING_STATUS=UNAVAILABLE\n")
                .replace("PING_MS=12.4\n", ""),
        )

        assertEquals(TelemetryPingStatus.UNAVAILABLE, sample.pingStatus)
        assertEquals(null, sample.pingMs)
        assertEquals(SshTelemetryStatus.PARTIAL, sample.status)
    }

    @Test
    fun `rejects duplicate fields`() {
        val raw = validPayload() + "LOAD_1=9.9\n"

        assertThrows(SshTelemetryParseException::class.java) {
            SshTelemetryParser.parse(raw)
        }
    }

    @Test
    fun `rejects missing required field`() {
        val raw = validPayload().replace("MEM_TOTAL_KB=8000000\n", "")

        assertThrows(SshTelemetryParseException::class.java) {
            SshTelemetryParser.parse(raw)
        }
    }

    @Test
    fun `rejects oversized payload before parsing`() {
        val raw = SshTelemetryParser.FORMAT_VERSION + "\n" + "X".repeat(SshTelemetryParser.MAX_PAYLOAD_BYTES)

        assertThrows(SshTelemetryParseException::class.java) {
            SshTelemetryParser.parse(raw)
        }
    }

    @Test
    fun `rejects network counter rollback`() {
        val raw = validPayload().replace("NET_B_RX_BYTES=102000\n", "NET_B_RX_BYTES=99000\n")

        assertThrows(SshTelemetryParseException::class.java) {
            SshTelemetryParser.parse(raw)
        }
    }

    @Test
    fun `rejects ping time when ping failed`() {
        val raw = validPayload().replace("PING_STATUS=OK", "PING_STATUS=FAILED")

        assertThrows(SshTelemetryParseException::class.java) {
            SshTelemetryParser.parse(raw)
        }
    }

    @Test
    fun `rejects unknown version`() {
        val raw = validPayload().replace(SshTelemetryParser.FORMAT_VERSION, "BLACKSERV_TELEMETRY_V2")

        assertThrows(SshTelemetryParseException::class.java) {
            SshTelemetryParser.parse(raw)
        }
    }

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
