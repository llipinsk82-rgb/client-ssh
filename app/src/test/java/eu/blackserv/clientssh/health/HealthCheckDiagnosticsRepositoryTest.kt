package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckDiagnosticsRepositoryTest {
    @Test
    fun `run survives repository recreation`() {
        val storage = MemoryStorage()
        val first = HealthCheckDiagnosticsRepository(storage)
        first.markStarted("profile-a", 100)
        first.markFinished("profile-a", 100, 140, HealthCheckRunOutcome.SUCCESS, "ONLINE")

        val restored = HealthCheckDiagnosticsRepository(storage).get("profile-a")

        assertEquals(100L, restored?.startedAt)
        assertEquals(140L, restored?.finishedAt)
        assertEquals(HealthCheckRunOutcome.SUCCESS, restored?.outcome)
        assertEquals("ONLINE", restored?.detail)
    }

    @Test
    fun `older worker cannot overwrite newer run`() {
        val repository = HealthCheckDiagnosticsRepository(MemoryStorage())
        repository.markStarted("profile-a", 200)
        repository.markFinished("profile-a", 200, 220, HealthCheckRunOutcome.SUCCESS)
        repository.markFinished("profile-a", 100, 300, HealthCheckRunOutcome.RETRY)

        assertEquals(HealthCheckRunOutcome.SUCCESS, repository.get("profile-a")?.outcome)
        assertEquals(200L, repository.get("profile-a")?.startedAt)
    }

    @Test
    fun `detail is single line and bounded`() {
        val repository = HealthCheckDiagnosticsRepository(MemoryStorage())
        repository.markFinished(
            profileId = "profile-a",
            startedAt = 1,
            finishedAt = 2,
            outcome = HealthCheckRunOutcome.RETRY,
            detail = "error\n" + "x".repeat(300),
        )

        val detail = repository.get("profile-a")!!.detail
        assertTrue('\n' !in detail)
        assertTrue(detail.length <= 160)
    }

    @Test
    fun `remove deletes only requested profile`() {
        val repository = HealthCheckDiagnosticsRepository(MemoryStorage())
        repository.markStarted("a", 1)
        repository.markStarted("b", 2)

        assertTrue(repository.remove("a"))
        assertNull(repository.get("a"))
        assertEquals("b", repository.getAll().single().profileId)
    }

    private class MemoryStorage : HealthCheckStorage {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }
}
