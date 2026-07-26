package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthMonitorControllerTest {
    @Test
    fun `saving enabled config schedules work`() {
        val fixture = Fixture()

        val saved = fixture.controller.save(
            HealthMonitorConfig(profileId = "a", enabled = true),
        )

        assertTrue(saved.enabled)
        assertEquals(listOf("a"), fixture.scheduler.scheduled)
        assertTrue(fixture.scheduler.cancelled.isEmpty())
    }

    @Test
    fun `disabling config cancels work`() {
        val fixture = Fixture()
        fixture.controller.save(HealthMonitorConfig(profileId = "a", enabled = true))

        fixture.controller.setEnabled("a", false)

        assertEquals(listOf("a"), fixture.scheduler.cancelled)
        assertEquals(false, fixture.configRepository.get("a")?.enabled)
    }

    @Test
    fun `testing background worker requires enabled monitoring and schedules one time work`() {
        val fixture = Fixture()
        fixture.controller.save(HealthMonitorConfig(profileId = "a", enabled = true))

        fixture.controller.testBackgroundWorkerNow("a")

        assertEquals(listOf("a"), fixture.scheduler.runNowRequests)
    }

    @Test(expected = IllegalStateException::class)
    fun `testing background worker rejects missing config`() {
        Fixture().controller.testBackgroundWorkerNow("missing")
    }

    @Test(expected = IllegalStateException::class)
    fun `testing background worker rejects disabled monitoring`() {
        val fixture = Fixture()
        fixture.controller.save(HealthMonitorConfig(profileId = "a", enabled = false))

        fixture.controller.testBackgroundWorkerNow("a")
    }

    @Test
    fun `removing profile cancels work and deletes all persisted health data`() {
        val fixture = Fixture()
        fixture.controller.save(HealthMonitorConfig(profileId = "a", enabled = true))
        fixture.snapshotRepository.upsert(
            HealthCheckSnapshot(profileId = "a", status = HealthStatus.OFFLINE),
        )
        fixture.historyRepository.append(
            HealthCheckRecord(
                profileId = "a",
                checkedAt = 1_000L,
                status = HealthStatus.OFFLINE,
                message = "timeout",
            ),
        )
        fixture.historyRepository.append(
            HealthCheckRecord(
                profileId = "b",
                checkedAt = 2_000L,
                status = HealthStatus.ONLINE,
                responseTimeMs = 12L,
            ),
        )
        fixture.telemetryRepository.append(
            SshTelemetryRecord.success("a", 1_000L, telemetrySample()),
        )
        fixture.telemetryRepository.append(
            SshTelemetryRecord.success("b", 2_000L, telemetrySample()),
        )

        fixture.controller.removeProfile("a")

        assertEquals("a", fixture.scheduler.cancelled.last())
        assertNull(fixture.configRepository.get("a"))
        assertNull(fixture.snapshotRepository.get("a"))
        assertTrue(fixture.historyRepository.get("a").isEmpty())
        assertEquals(1, fixture.historyRepository.get("b").size)
        assertNull(fixture.telemetryRepository.latest("a"))
        assertEquals("b", fixture.telemetryRepository.latestAll().single().profileId)
    }

    private class Fixture {
        val scheduler = RecordingScheduler()
        val configRepository = HealthMonitorConfigRepository(MemoryStorage())
        val snapshotRepository = HealthCheckRepository(MemoryStorage())
        val historyRepository = HealthCheckHistoryRepository(MemoryStorage())
        val telemetryRepository = SshTelemetryRepository(MemoryStorage())
        val controller = HealthMonitorController(
            configRepository = configRepository,
            snapshotRepository = snapshotRepository,
            scheduler = scheduler,
            historyRepository = historyRepository,
            telemetryRepository = telemetryRepository,
        )
    }

    private class RecordingScheduler : HealthWorkScheduler {
        val scheduled = mutableListOf<String>()
        val runNowRequests = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override fun schedule(config: HealthMonitorConfig) {
            scheduled += config.profileId
        }

        override fun runNow(profileId: String) {
            runNowRequests += profileId
        }

        override fun cancel(profileId: String) {
            cancelled += profileId
        }
    }

    private class MemoryStorage : HealthCheckStorage {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }

    private companion object {
        fun telemetrySample() = SshTelemetrySample(
            cpuUsagePercent = 10.0,
            memoryTotalKb = 1_000,
            memoryAvailableKb = 500,
            load1 = 0.1,
            load5 = 0.1,
            load15 = 0.1,
            diskTotalKb = 10_000,
            diskUsedKb = 4_000,
            diskAvailableKb = 6_000,
            diskUsedPercent = 40,
            uptimeSeconds = 100,
            networkRxBytesPerSecond = 10,
            networkTxBytesPerSecond = 20,
            pingStatus = TelemetryPingStatus.OK,
            pingMs = 5.0,
        )
    }
}
