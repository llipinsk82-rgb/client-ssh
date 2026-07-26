package eu.blackserv.clientssh.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalCompletionInputTest {
    @Test
    fun `typed command is sent before tab`() {
        assertEquals("cd /op\t", terminalCompletionInput("cd /op"))
    }

    @Test
    fun `empty command sends only tab`() {
        assertEquals("\t", terminalCompletionInput(""))
    }

    @Test
    fun `typed interactive response is sent before enter`() {
        assertEquals("y\r", terminalSubmitInput("y"))
    }

    @Test
    fun `empty interactive response sends only enter`() {
        assertEquals("\r", terminalSubmitInput(""))
    }
}
