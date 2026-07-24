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
    fun `removing profile cancels work and deletes all persisted health data`() {
        val fixture = Fixture()
        fixture.controller.save(HealthMonitorConfig(profileId = "a", enabled = true))
        fixture.snapshotRepository.upsert(
            HealthCheckSnapshot(profileId = "a", status = HealthStatus.OFFLINE),
        )

        fixture.controller.removeProfile("a")

        assertEquals("a", fixture.scheduler.cancelled.last())
        assertNull(fixture.configRepository.get("a"))
        assertNull(fixture.snapshotRepository.get("a"))
    }

    private class Fixture {
        val scheduler = RecordingScheduler()
        val configRepository = HealthMonitorConfigRepository(MemoryStorage())
        val snapshotRepository = HealthCheckRepository(MemoryStorage())
        val controller = HealthMonitorController(configRepository, snapshotRepository, scheduler)
    }

    private class RecordingScheduler : HealthWorkScheduler {
        val scheduled = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override fun schedule(config: HealthMonitorConfig) {
            scheduled += config.profileId
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
}
