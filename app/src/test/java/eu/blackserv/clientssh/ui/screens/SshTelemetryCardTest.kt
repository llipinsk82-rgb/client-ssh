package eu.blackserv.clientssh.ui.screens

import eu.blackserv.clientssh.health.SshTelemetryFailureKind
import eu.blackserv.clientssh.health.TelemetryPingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SshTelemetryCardTest {
    @Test
    fun `formats percentages and load with stable decimal separator`() {
        assertEquals("12.3%", formatPercent(12.34))
        assertEquals("0.8", formatDecimal(0.75))
    }

    @Test
    fun `formats binary sizes and rates`() {
        assertEquals("1.0 MiB", formatBytesFromKb(1_024))
        assertEquals("2.0 KiB/s", formatRate(2_048))
    }

    @Test
    fun `formats uptime without seconds noise`() {
        assertEquals("2d 3h", formatUptime(183_600))
        assertEquals("4h 5m", formatUptime(14_700))
        assertEquals("9m", formatUptime(599))
    }

    @Test
    fun `formats all ping states explicitly`() {
        assertEquals("10.5 ms", formatPing(TelemetryPingStatus.OK, 10.5))
        assertEquals("WYŁĄCZONY", formatPing(TelemetryPingStatus.DISABLED, null))
        assertEquals("BRAK NARZĘDZIA PING", formatPing(TelemetryPingStatus.UNAVAILABLE, null))
        assertEquals("BRAK ODPOWIEDZI", formatPing(TelemetryPingStatus.FAILED, null))
    }

    @Test
    fun `maps sensitive transport failures to controlled labels`() {
        assertEquals(
            "HOST KEY NIEZAAKCEPTOWANY",
            telemetryFailureLabel(SshTelemetryFailureKind.HOST_KEY_NOT_TRUSTED),
        )
        assertEquals(
            "BŁĄD UWIERZYTELNIENIA",
            telemetryFailureLabel(SshTelemetryFailureKind.AUTHENTICATION_FAILED),
        )
        assertEquals(
            "TELEMETRIA NIEUDANA",
            telemetryFailureLabel(SshTelemetryFailureKind.INTERNAL_ERROR),
        )
    }
}
