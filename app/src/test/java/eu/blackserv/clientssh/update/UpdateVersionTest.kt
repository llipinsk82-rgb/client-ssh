package eu.blackserv.clientssh.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun newerPatchIsDetected() {
        assertTrue(isNewerVersion("0.3.7", "0.3.6"))
    }

    @Test
    fun equalVersionIsNotAnUpdate() {
        assertFalse(isNewerVersion("0.3.7", "0.3.7"))
    }

    @Test
    fun olderVersionIsRejected() {
        assertFalse(isNewerVersion("0.3.6", "0.3.7"))
    }

    @Test
    fun missingPartsAreTreatedAsZero() {
        assertFalse(isNewerVersion("1.0", "1.0.0"))
        assertTrue(isNewerVersion("1.0.1", "1"))
    }

    @Test
    fun prereleaseSuffixDoesNotBreakNumericComparison() {
        assertTrue(isNewerVersion("0.3.8-beta1", "0.3.7"))
    }
}
