package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckRepositoryTest {
    @Test
    fun `state survives repository recreation`() {
        val storage = MemoryStorage()
        val first = HealthCheckRepository(storage)

        first.applyObservation(
            profileId = "profile-a",
            observation = HealthObservation.Success(responseTimeMs = 42),
            now = 1000,
            offlineFailureThreshold = 3,
        )

        val restored = HealthCheckRepository(storage).get("profile-a")

        assertEquals(HealthStatus.ONLINE, restored?.status)
        assertEquals(42L, restored?.responseTimeMs)
        assertEquals(1000L, restored?.lastSuccessAt)
    }

    @Test
    fun `failure counter survives restart and confirms offline once`() {
        val storage = MemoryStorage()
        HealthCheckRepository(storage).applyObservation(
            profileId = "profile-a",
            observation = HealthObservation.Failure("timeout"),
            now = 1000,
            offlineFailureThreshold = 2,
        )

        val restored = HealthCheckRepository(storage)
        val transition = restored.applyObservation(
            profileId = "profile-a",
            observation = HealthObservation.Failure("timeout"),
            now = 2000,
            offlineFailureThreshold = 2,
        )
        val repeated = restored.applyObservation(
            profileId = "profile-a",
            observation = HealthObservation.Failure("timeout"),
            now = 3000,
            offlineFailureThreshold = 2,
        )

        assertEquals(HealthStatus.OFFLINE, transition.snapshot.status)
        assertTrue(transition.notifyStatusChange)
        assertFalse(repeated.notifyStatusChange)
        assertEquals(3, repeated.snapshot.consecutiveFailures)
    }

    @Test
    fun `codec preserves escaped message characters`() {
        val storage = MemoryStorage()
        val repository = HealthCheckRepository(storage)
        repository.upsert(
            HealthCheckSnapshot(
                profileId = "profile%a",
                status = HealthStatus.OFFLINE,
                consecutiveFailures = 4,
                lastCheckedAt = 100,
                message = "timeout\nport\t22%",
            ),
        )

        val restored = HealthCheckRepository(storage).get("profile%a")

        assertEquals("timeout\nport\t22%", restored?.message)
        assertEquals(4, restored?.consecutiveFailures)
    }

    @Test
    fun `malformed rows are ignored without losing valid rows`() {
        val storage = MemoryStorage(
            "v1\ninvalid\nprofile-a\tONLINE\t0\t100\t100\t12\tok\n",
        )

        val items = HealthCheckRepository(storage).getAll()

        assertEquals(1, items.size)
        assertEquals("profile-a", items.single().profileId)
    }

    @Test
    fun `remove deletes only requested profile`() {
        val storage = MemoryStorage()
        val repository = HealthCheckRepository(storage)
        repository.upsert(HealthCheckSnapshot(profileId = "a"))
        repository.upsert(HealthCheckSnapshot(profileId = "b"))

        assertTrue(repository.remove("a"))
        assertFalse(repository.remove("missing"))
        assertNull(repository.get("a"))
        assertEquals("b", repository.getAll().single().profileId)
    }

    private class MemoryStorage(initial: String? = null) : HealthCheckStorage {
        private var value: String? = initial

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
