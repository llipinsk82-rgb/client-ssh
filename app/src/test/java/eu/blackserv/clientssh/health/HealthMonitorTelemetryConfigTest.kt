package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthMonitorTelemetryConfigTest {
    @Test
    fun `v2 codec preserves telemetry and ping settings`() {
        val storage = MemoryStorage()
        HealthMonitorConfigRepository(storage).upsert(
            HealthMonitorConfig(
                profileId = "profile-a",
                enabled = true,
                sshTelemetryEnabled = true,
                pingEnabled = true,
                pingTarget = "status.example.com",
            ),
        )

        val restored = HealthMonitorConfigRepository(storage).get("profile-a")!!

        assertTrue(restored.enabled)
        assertTrue(restored.sshTelemetryEnabled)
        assertTrue(restored.pingEnabled)
        assertEquals("status.example.com", restored.pingTarget)
        assertEquals("status.example.com", restored.resolvedPingTarget()?.value)
    }

    @Test
    fun `v1 codec migrates with telemetry disabled`() {
        val storage = MemoryStorage("v1\nprofile-a\ttrue\t30\t2500\t4\n")

        val restored = HealthMonitorConfigRepository(storage).get("profile-a")!!

        assertTrue(restored.enabled)
        assertEquals(30L, restored.intervalMinutes)
        assertEquals(2_500, restored.timeoutMs)
        assertEquals(4, restored.offlineFailureThreshold)
        assertFalse(restored.sshTelemetryEnabled)
        assertTrue(restored.pingEnabled)
        assertEquals(HealthMonitorConfig.DEFAULT_PING_TARGET, restored.pingTarget)
    }

    @Test
    fun `disabled ping accepts blank target`() {
        val config = HealthMonitorConfig(
            profileId = "profile-a",
            sshTelemetryEnabled = true,
            pingEnabled = false,
            pingTarget = "",
        )

        assertNull(config.resolvedPingTarget())
    }

    @Test
    fun `enabled ping rejects unsafe target`() {
        assertThrows(IllegalArgumentException::class.java) {
            HealthMonitorConfig(
                profileId = "profile-a",
                sshTelemetryEnabled = true,
                pingEnabled = true,
                pingTarget = "1.1.1.1;id",
            )
        }
    }

    private class MemoryStorage(initial: String? = null) : HealthCheckStorage {
        var value: String? = initial

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
