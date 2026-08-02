package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckHistoryRepositoryTest {
    @Test
    fun `history survives repository recreation`() {
        val storage = MemoryStorage()
        HealthCheckHistoryRepository(storage).append(
            HealthCheckRecord("profile-a", 1000, HealthStatus.ONLINE, 42, "ok"),
        )

        val restored = HealthCheckHistoryRepository(storage).get("profile-a").single()

        assertEquals(1000L, restored.checkedAt)
        assertEquals(HealthStatus.ONLINE, restored.status)
        assertEquals(42L, restored.responseTimeMs)
    }

    @Test
    fun `history keeps newest bounded entries per profile`() {
        val repository = HealthCheckHistoryRepository(MemoryStorage(), maxEntriesPerProfile = 2)
        repository.append(HealthCheckRecord("a", 1, HealthStatus.ONLINE))
        repository.append(HealthCheckRecord("a", 2, HealthStatus.OFFLINE))
        repository.append(HealthCheckRecord("a", 3, HealthStatus.ONLINE))
        repository.append(HealthCheckRecord("b", 1, HealthStatus.ONLINE))

        assertEquals(listOf(3L, 2L), repository.get("a").map { it.checkedAt })
        assertEquals(1, repository.get("b").size)
    }

    @Test
    fun `remove profile does not remove other history`() {
        val repository = HealthCheckHistoryRepository(MemoryStorage())
        repository.append(HealthCheckRecord("a", 1, HealthStatus.ONLINE))
        repository.append(HealthCheckRecord("b", 2, HealthStatus.OFFLINE, message = "timeout"))

        assertTrue(repository.removeProfile("a"))
        assertFalse(repository.removeProfile("missing"))
        assertTrue(repository.get("a").isEmpty())
        assertEquals("timeout", repository.get("b").single().message)
    }

    @Test
    fun `malformed rows are ignored`() {
        val storage = MemoryStorage("v1\ninvalid\na\t100\tONLINE\t12\tok\n")

        assertEquals(1, HealthCheckHistoryRepository(storage).get("a").size)
    }

    private class MemoryStorage(initial: String? = null) : HealthCheckStorage {
        private var value = initial
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
    }
}
