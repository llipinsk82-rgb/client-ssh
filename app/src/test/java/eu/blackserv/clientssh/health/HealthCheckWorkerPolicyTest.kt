package eu.blackserv.clientssh.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckWorkerPolicyTest {
    @Test
    fun retriesBeforeAttemptLimit() {
        repeat(HealthCheckWorker.MAX_RETRY_ATTEMPTS) { attempt ->
            val decision = healthWorkerFailureDecision(attempt)

            assertTrue(decision.shouldRetry)
            assertEquals(HealthCheckRunOutcome.RETRY, decision.outcome)
        }
    }

    @Test
    fun failsAfterAttemptLimitIsReached() {
        val decision = healthWorkerFailureDecision(HealthCheckWorker.MAX_RETRY_ATTEMPTS)

        assertFalse(decision.shouldRetry)
        assertEquals(HealthCheckRunOutcome.FAILED, decision.outcome)
    }

    @Test
    fun rejectsNegativeAttemptCount() {
        assertThrows(IllegalArgumentException::class.java) {
            healthWorkerFailureDecision(-1)
        }
    }

    @Test
    fun `unexpected worker detail never exposes exception message`() {
        val marker = "worker-sensitive-marker"

        val detail = healthWorkerSafeFailureDetail(IllegalStateException(marker))

        assertFalse(detail.contains(marker))
        assertTrue(detail.contains("IllegalStateException"))
    }
}
