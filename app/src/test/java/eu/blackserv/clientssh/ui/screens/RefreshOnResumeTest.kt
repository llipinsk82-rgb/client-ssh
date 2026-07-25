package eu.blackserv.clientssh.ui.screens

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshOnResumeTest {
    @Test
    fun `refreshes only when screen resumes`() {
        Lifecycle.Event.entries.forEach { event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                assertTrue(shouldRefreshHealthMonitor(event))
            } else {
                assertFalse("Unexpected refresh for $event", shouldRefreshHealthMonitor(event))
            }
        }
    }
}
