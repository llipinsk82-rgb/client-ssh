package eu.blackserv.clientssh.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScrollFollowTest {
    @Test
    fun `stays in follow mode at bottom`() {
        assertTrue(shouldFollowTerminalOutputAfterUserScroll(scrollValue = 952, maxValue = 1_000))
    }

    @Test
    fun `leaves follow mode when user scrolls away from bottom`() {
        assertFalse(shouldFollowTerminalOutputAfterUserScroll(scrollValue = 700, maxValue = 1_000))
    }

    @Test
    fun `handles short content without disabling follow mode`() {
        assertTrue(shouldFollowTerminalOutputAfterUserScroll(scrollValue = 0, maxValue = 0))
    }
}
