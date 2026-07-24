package eu.blackserv.clientssh.ui.screens

import org.junit.Assert.assertEquals
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
}
