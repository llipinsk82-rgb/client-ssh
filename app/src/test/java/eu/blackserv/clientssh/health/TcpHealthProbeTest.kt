package eu.blackserv.clientssh.health

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpHealthProbeTest {
    @Test
    fun successfulConnectorReturnsSuccessObservation() {
        val probe = TcpHealthProbe(TcpConnector { 27L })

        val result = probe.check(HealthTarget("example.com", 22))

        assertEquals(HealthObservation.Success(27L), result)
    }

    @Test
    fun negativeConnectorDurationIsClampedToZero() {
        val probe = TcpHealthProbe(TcpConnector { -5L })

        val result = probe.check(HealthTarget("example.com", 22))

        assertEquals(HealthObservation.Success(0L), result)
    }

    @Test
    fun connectorFailureReturnsSanitizedFailureObservation() {
        val probe = TcpHealthProbe(TcpConnector {
            throw IOException("Connection refused\ninternal detail")
        })

        val result = probe.check(HealthTarget("example.com", 22))

        assertTrue(result is HealthObservation.Failure)
        assertEquals(
            "IOException: Connection refused internal detail",
            (result as HealthObservation.Failure).message,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankHost() {
        HealthTarget("   ", 22)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPort() {
        HealthTarget("example.com", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeTimeout() {
        HealthTarget("example.com", 22, timeoutMs = 100)
    }
}
