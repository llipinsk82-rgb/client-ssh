package eu.blackserv.clientssh.ui.screens

import eu.blackserv.clientssh.health.HealthMonitorConfig
import eu.blackserv.clientssh.health.SshTelemetryFailureKind
import eu.blackserv.clientssh.health.TelemetryPingStatus
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `main summary hides disabled optional ICMP`() {
        assertNull(compactIcmpLabel(TelemetryPingStatus.DISABLED, null))
        assertEquals("ICMP 8.4 ms", compactIcmpLabel(TelemetryPingStatus.OK, 8.4))
        assertEquals("ICMP niedostępny", compactIcmpLabel(TelemetryPingStatus.UNAVAILABLE, null))
    }

    @Test
    fun `one click enables full telemetry for supported SSH profiles`() {
        val passwordProfile = profile(AuthenticationMethod.PASSWORD)
        val keyProfile = profile(AuthenticationMethod.PRIVATE_KEY)

        assertTrue(
            configForFullManualCheck(
                passwordProfile,
                HealthMonitorConfig(profileId = passwordProfile.id, sshTelemetryEnabled = false),
            ).sshTelemetryEnabled,
        )
        assertTrue(
            configForFullManualCheck(
                keyProfile,
                HealthMonitorConfig(profileId = keyProfile.id, sshTelemetryEnabled = false),
            ).sshTelemetryEnabled,
        )
    }

    @Test
    fun `one click does not start background unsafe interactive telemetry`() {
        val interactiveProfile = profile(AuthenticationMethod.INTERACTIVE)
        val telnetProfile = profile(
            authenticationMethod = AuthenticationMethod.PASSWORD,
            protocol = ConnectionProtocol.TELNET,
        )

        assertFalse(
            configForFullManualCheck(
                interactiveProfile,
                HealthMonitorConfig(profileId = interactiveProfile.id, sshTelemetryEnabled = true),
            ).sshTelemetryEnabled,
        )
        assertFalse(
            configForFullManualCheck(
                telnetProfile,
                HealthMonitorConfig(profileId = telnetProfile.id, sshTelemetryEnabled = true),
            ).sshTelemetryEnabled,
        )
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

    private fun profile(
        authenticationMethod: AuthenticationMethod,
        protocol: ConnectionProtocol = ConnectionProtocol.SSH,
    ): HostProfile = HostProfile(
        id = "profile-${protocol.name}-${authenticationMethod.name}",
        name = "Test",
        host = "example.test",
        port = protocol.defaultPort,
        username = "root",
        protocol = protocol,
        authenticationMethod = authenticationMethod,
    )
}
