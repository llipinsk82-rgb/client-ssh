package eu.blackserv.clientssh.sftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpSecurityPolicyTest {
    @Test
    fun `SFTP requires a previously verified host key`() {
        assertEquals("yes", SFTP_STRICT_HOST_KEY_CHECKING)
    }

    @Test
    fun `unknown host key directs user to terminal verification`() {
        val message = safeSftpErrorMessage(
            IllegalStateException("UnknownHostKey: [example.org]:2222 ssh-ed25519 SECRET_BLOB"),
            "example.org",
        )

        assertTrue(message.contains("nie jest zweryfikowany", ignoreCase = true))
        assertTrue(message.contains("Terminal", ignoreCase = true))
        assertFalse(message.contains("SECRET_BLOB"))
    }

    @Test
    fun `changed host key is always blocked`() {
        val message = safeSftpErrorMessage(
            IllegalStateException("HostKey has been changed: [example.org]:2222 SECRET_BLOB"),
            "example.org",
        )

        assertTrue(message.contains("zmienił się", ignoreCase = true))
        assertTrue(message.contains("zablokowane", ignoreCase = true))
        assertFalse(message.contains("SECRET_BLOB"))
    }

    @Test
    fun `unexpected SFTP exception is redacted`() {
        val message = safeSftpErrorMessage(
            IllegalStateException("token=super-secret internal-stack-detail"),
            "example.org",
        )

        assertEquals("Nie udało się wykonać operacji SFTP.", message)
        assertFalse(message.contains("super-secret"))
    }
}
