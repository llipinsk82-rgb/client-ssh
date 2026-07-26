package eu.blackserv.clientssh.backup

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BoundedBackupInputTest {
    @Test
    fun `reader accepts content exactly at the limit`() {
        val content = ByteArray(128) { it.toByte() }

        val loaded = ByteArrayInputStream(content).readBoundedBackup(128)

        assertArrayEquals(content, loaded)
    }

    @Test
    fun `reader rejects content above the limit`() {
        try {
            ByteArrayInputStream(ByteArray(129)).readBoundedBackup(128)
            fail("Oczekiwano ProfileBackupException")
        } catch (error: ProfileBackupException) {
            assertTrue(error.message.orEmpty().contains("rozmiar"))
        }
    }
}
