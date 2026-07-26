package eu.blackserv.clientssh.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshErrorPolicyTest {
    @Test
    fun `unknown transport error never exposes raw message`() {
        val secret = "-----BEGIN PRIVATE KEY----- do-not-leak"
        val message = IllegalStateException(secret).toSafeSshMessage("example.test")

        assertFalse(message.contains(secret))
        assertFalse(message.contains("PRIVATE KEY"))
        assertTrue(message.contains("SSH"))
    }

    @Test
    fun `authentication error is terminal and sanitized`() {
        val error = IllegalStateException("Auth fail for password super-secret")
        val message = error.toSafeSshMessage("example.test")

        assertFalse(error.isRetryableSshError())
        assertFalse(message.contains("super-secret"))
        assertTrue(message.contains("Nieprawidłowy login"))
    }

    @Test
    fun `network timeout remains retryable`() {
        val error = IllegalStateException("connection timeout while opening socket")

        assertTrue(error.isRetryableSshError())
        assertTrue(error.toSafeSshMessage("example.test").contains("czas oczekiwania"))
    }

    @Test
    fun `host key change is terminal`() {
        val error = IllegalStateException("Host key has changed")

        assertFalse(error.isRetryableSshError())
        assertTrue(error.toSafeSshMessage("example.test").contains("zablokowane"))
    }
}
