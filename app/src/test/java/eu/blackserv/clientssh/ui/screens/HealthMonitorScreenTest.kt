package eu.blackserv.clientssh.ui.screens

import android.os.Build
import eu.blackserv.clientssh.health.HealthCheckRunDiagnostic
import eu.blackserv.clientssh.health.HealthCheckRunOutcome
import eu.blackserv.clientssh.health.HealthCheckSnapshot
import eu.blackserv.clientssh.health.HealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthMonitorScreenTest {
    @Test
    fun `formats recent health check timestamps`() {
        val now = 10_000_000L

        assertEquals("przed chwilą", healthTimestampLabel(now - 30_000L, now))
        assertEquals("5 min temu", healthTimestampLabel(now - 5 * 60_000L, now))
        assertEquals("2 godz. temu", healthTimestampLabel(now - 2 * 3_600_000L, now))
    }

    @Test
    fun `future timestamp is treated as just now`() {
        assertEquals("przed chwilą", healthTimestampLabel(timestamp = 20_000L, now = 10_000L))
    }

    @Test
    fun `notification permission is not required before Android 13`() {
        assertTrue(
            healthNotificationsGranted(
                sdkInt = Build.VERSION_CODES.S_V2,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `notification permission reflects current Android 13 permission state`() {
        assertFalse(
            healthNotificationsGranted(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = false,
            ),
        )
        assertTrue(
            healthNotificationsGranted(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun `background report is useful but does not expose profile id`() {
        val profileId = "private-profile-id"
        val now = 20_000L
        val report = healthBackgroundReport(
            profileId = profileId,
            enabled = true,
            diagnostic = HealthCheckRunDiagnostic(
                profileId = profileId,
                startedAt = 10_000L,
                finishedAt = 11_000L,
                outcome = HealthCheckRunOutcome.SUCCESS,
                detail = "Pomiar zakończony",
            ),
            snapshot = HealthCheckSnapshot(
                profileId = profileId,
                status = HealthStatus.ONLINE,
                consecutiveFailures = 0,
                lastCheckedAt = 11_000L,
                lastSuccessAt = 11_000L,
                responseTimeMs = 42L,
                message = "OK",
            ),
            historySize = 3,
            now = now,
        )

        assertTrue(report.contains("worker=SUCCESS"))
        assertTrue(report.contains("status=ONLINE"))
        assertTrue(report.contains("history_records=3"))
        assertFalse(report.contains(profileId))
    }
}
