package eu.blackserv.clientssh.terminal

import eu.blackserv.clientssh.model.TextWrapMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalWrapPreferencesTest {
    @Test
    fun `missing or invalid preference defaults to automatic wrapping`() {
        assertEquals(TextWrapMode.WRAP, TerminalWrapPreferences.decode(null))
        assertEquals(TextWrapMode.WRAP, TerminalWrapPreferences.decode("invalid"))
    }

    @Test
    fun `both wrap modes round trip through private preference value`() {
        TextWrapMode.entries.forEach { mode ->
            assertEquals(mode, TerminalWrapPreferences.decode(TerminalWrapPreferences.encode(mode)))
        }
    }
}
