package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthMonitorConfigRepositoryTest {
    @Test
    fun `configuration survives repository recreation`() {
        val storage = MemoryStorage()
        HealthMonitorConfigRepository(storage).upsert(
            HealthMonitorConfig(
                profileId = "profile-a",
                enabled = true,
                intervalMinutes = 30,
                timeoutMs = 2_500,
                offlineFailureThreshold = 4,
            ),
        )

        val restored = HealthMonitorConfigRepository(storage).get("profile-a")

        assertEquals(true, restored?.enabled)
        assertEquals(30L, restored?.intervalMinutes)
        assertEquals(2_500, restored?.timeoutMs)
        assertEquals(4, restored?.offlineFailureThreshold)
    }

    @Test
    fun `set enabled creates defaults and can disable existing config`() {
        val repository = HealthMonitorConfigRepository(MemoryStorage())

        val enabled = repository.setEnabled("profile-a", true)
        val disabled = repository.setEnabled("profile-a", false)

        assertTrue(enabled.enabled)
        assertFalse(disabled.enabled)
        assertEquals(HealthMonitorConfig.DEFAULT_INTERVAL_MINUTES, disabled.intervalMinutes)
        assertEquals(HealthMonitorConfig.DEFAULT_TIMEOUT_MS, disabled.timeoutMs)
    }

    @Test
    fun `enabled list contains only enabled profiles in stable order`() {
        val repository = HealthMonitorConfigRepository(MemoryStorage())
        repository.upsert(HealthMonitorConfig(profileId = "z", enabled = true))
        repository.upsert(HealthMonitorConfig(profileId = "a", enabled = false))
        repository.upsert(HealthMonitorConfig(profileId = "b", enabled = true))

        assertEquals(listOf("b", "z"), repository.getEnabled().map { it.profileId })
    }

    @Test
    fun `multiple repository instances do not lose profiles`() {
        val storage = MemoryStorage()
        val first = HealthMonitorConfigRepository(storage)
        val second = HealthMonitorConfigRepository(storage)

        first.upsert(HealthMonitorConfig(profileId = "a", enabled = true))
        second.upsert(HealthMonitorConfig(profileId = "b", enabled = true))

        assertEquals(listOf("a", "b"), first.getAll().map { it.profileId })
    }

    @Test
    fun `codec preserves escaped profile id and skips malformed rows`() {
        val storage = MemoryStorage()
        val repository = HealthMonitorConfigRepository(storage)
        repository.upsert(HealthMonitorConfig(profileId = "profile%\t\n", enabled = true))
        storage.value = storage.value + "broken\trow\n"

        val restored = HealthMonitorConfigRepository(storage).getAll()

        assertEquals(1, restored.size)
        assertEquals("profile%\t\n", restored.single().profileId)
    }

    @Test
    fun `remove deletes only requested config`() {
        val repository = HealthMonitorConfigRepository(MemoryStorage())
        repository.upsert(HealthMonitorConfig(profileId = "a"))
        repository.upsert(HealthMonitorConfig(profileId = "b"))

        assertTrue(repository.remove("a"))
        assertFalse(repository.remove("missing"))
        assertNull(repository.get("a"))
        assertEquals("b", repository.getAll().single().profileId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects interval below WorkManager minimum`() {
        HealthMonitorConfig(profileId = "a", intervalMinutes = 14)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unreasonable timeout`() {
        HealthMonitorConfig(profileId = "a", timeoutMs = 60_000)
    }

    private class MemoryStorage(initial: String? = null) : HealthCheckStorage {
        var value: String? = initial

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
