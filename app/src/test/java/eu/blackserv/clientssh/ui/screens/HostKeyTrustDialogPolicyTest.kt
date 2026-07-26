package eu.blackserv.clientssh.ui.screens

import eu.blackserv.clientssh.ssh.HostKeyTrustKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyTrustDialogPolicyTest {
    @Test
    fun `unknown host key can expose explicit trust action`() {
        assertTrue(canAcceptHostKey(HostKeyTrustKind.UNKNOWN))
    }

    @Test
    fun `changed host key can never expose trust action`() {
        assertFalse(canAcceptHostKey(HostKeyTrustKind.CHANGED))
    }
}
