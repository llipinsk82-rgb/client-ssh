package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshTelemetryRepositoryTest {
    @Test
    fun `success sample survives repository recreation`() {
        val storage = MemoryStorage()
        SshTelemetryRepository(storage).append(
            SshTelemetryRecord.success("profile-a", 1234L, sample()),
        )

        val restored = SshTelemetryRepository(storage).latest("profile-a")

        assertEquals(SshTelemetryRecordOutcome.SUCCESS, restored?.outcome)
        assertEquals(25.0, restored?.sample?.cpuUsagePercent ?: -1.0, 0.001)
        assertEquals(2_000L, restored?.sample?.networkRxBytesPerSecond)
        assertEquals(TelemetryPingStatus.OK, restored?.sample?.pingStatus)
    }

    @Test
    fun `failure stores only safe bounded message`() {
        val repository = SshTelemetryRepository(MemoryStorage())
        repository.append(
            SshTelemetryRecord.failure(
                profileId = "profile-a",
                collectedAt = 1L,
                kind = SshTelemetryFailureKind.AUTHENTICATION_FAILED,
                message = "line-one\nline-two\t" + "x".repeat(300),
            ),
        )

        val restored = repository.latest("profile-a")!!

        assertEquals(SshTelemetryRecordOutcome.FAILURE, restored.outcome)
        assertNull(restored.sample)
        assertEquals(SshTelemetryFailureKind.AUTHENTICATION_FAILED, restored.failureKind)
        assertFalse(restored.message.contains('\n'))
        assertFalse(restored.message.contains('\t'))
        assertTrue(restored.message.length <= 200)
    }

    @Test
    fun `history is bounded per profile`() {
        val repository = SshTelemetryRepository(MemoryStorage())
        repeat(SshTelemetryRepository.MAX_RECORDS_PER_PROFILE + 10) { index ->
            repository.append(
                SshTelemetryRecord.success(
                    profileId = "profile-a",
                    collectedAt = index.toLong(),
                    sample = sample(),
                ),
            )
        }
        repository.append(SshTelemetryRecord.success("profile-b", 1L, sample()))

        val history = repository.history(
            "profile-a",
            SshTelemetryRepository.MAX_RECORDS_PER_PROFILE,
        )

        assertEquals(SshTelemetryRepository.MAX_RECORDS_PER_PROFILE, history.size)
        assertEquals(105L, history.first().collectedAt)
        assertEquals(10L, history.last().collectedAt)
        assertEquals(1, repository.history("profile-b").size)
    }

    @Test
    fun `latest chooses newest record`() {
        val repository = SshTelemetryRepository(MemoryStorage())
        repository.append(SshTelemetryRecord.success("profile-a", 10L, sample()))
        repository.append(
            SshTelemetryRecord.failure(
                "profile-a",
                20L,
                SshTelemetryFailureKind.COMMAND_TIMEOUT,
                "timeout",
            ),
        )

        assertEquals(20L, repository.latest("profile-a")?.collectedAt)
        assertEquals(SshTelemetryRecordOutcome.FAILURE, repository.latest("profile-a")?.outcome)
    }

    @Test
    fun `malformed rows are ignored`() {
        val storage = MemoryStorage()
        val repository = SshTelemetryRepository(storage)
        repository.append(SshTelemetryRecord.success("profile-a", 1L, sample()))
        storage.value = storage.value + "broken\trow\n"

        assertEquals(1, SshTelemetryRepository(storage).history("profile-a").size)
    }

    @Test
    fun `remove deletes only requested profile`() {
        val repository = SshTelemetryRepository(MemoryStorage())
        repository.append(SshTelemetryRecord.success("profile-a", 1L, sample()))
        repository.append(SshTelemetryRecord.success("profile-b", 1L, sample()))

        assertTrue(repository.remove("profile-a"))
        assertFalse(repository.remove("missing"))
        assertNull(repository.latest("profile-a"))
        assertEquals("profile-b", repository.latestAll().single().profileId)
    }

    private fun sample() = SshTelemetrySample(
        cpuUsagePercent = 25.0,
        memoryTotalKb = 8_000,
        memoryAvailableKb = 4_000,
        load1 = 0.5,
        load5 = 0.4,
        load15 = 0.3,
        diskTotalKb = 100_000,
        diskUsedKb = 40_000,
        diskAvailableKb = 60_000,
        diskUsedPercent = 40,
        uptimeSeconds = 3_600,
        networkRxBytesPerSecond = 2_000,
        networkTxBytesPerSecond = 1_000,
        pingStatus = TelemetryPingStatus.OK,
        pingMs = 10.5,
    )

    private class MemoryStorage : HealthCheckStorage {
        var value: String? = null

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }
    }
}
