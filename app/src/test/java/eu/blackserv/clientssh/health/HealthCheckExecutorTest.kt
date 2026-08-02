package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HealthCheckExecutorTest {
    @Test
    fun `success is persisted with executor clock`() {
        val storage = MemoryStorage()
        val repository = HealthCheckRepository(storage)
        val executor = HealthCheckExecutor(
            snapshotRepository = repository,
            probe = HealthProbe { HealthObservation.Success(27L) },
            clock = { 1234L },
        )

        val transition = executor.execute(
            profileId = "profile-a",
            target = HealthTarget("example.org", 22),
            offlineFailureThreshold = 3,
        )

        assertEquals(HealthStatus.ONLINE, transition.snapshot.status)
        assertEquals(27L, transition.snapshot.responseTimeMs)
        assertEquals(1234L, repository.get("profile-a")?.lastCheckedAt)
        assertFalse(transition.notifyStatusChange)
    }

    @Test
    fun `failure uses configured offline threshold`() {
        val storage = MemoryStorage()
        val repository = HealthCheckRepository(storage)
        val executor = HealthCheckExecutor(
            snapshotRepository = repository,
            probe = HealthProbe { HealthObservation.Failure("timeout") },
            clock = { 2000L },
        )

        executor.execute("profile-a", HealthTarget("example.org", 22), 2)
        val transition = executor.execute("profile-a", HealthTarget("example.org", 22), 2)

        assertEquals(HealthStatus.OFFLINE, transition.snapshot.status)
        assertEquals(2, transition.snapshot.consecutiveFailures)
        assertEquals(true, transition.notifyStatusChange)
    }

    private class MemoryStorage : HealthCheckStorage {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }
}
