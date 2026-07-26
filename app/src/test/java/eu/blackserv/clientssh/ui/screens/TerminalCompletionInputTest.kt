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
    fun `interactive answer is terminated with line feed`() {
        assertEquals("y\n", terminalSubmitInput("y"))
    }

    @Test
    fun `regular command uses the same line feed submit path`() {
        assertEquals("gh auth status\n", terminalSubmitInput("gh auth status"))
    }

    @Test
    fun `empty enter sends only line feed`() {
        assertEquals("\n", terminalSubmitInput(""))
    }
}
