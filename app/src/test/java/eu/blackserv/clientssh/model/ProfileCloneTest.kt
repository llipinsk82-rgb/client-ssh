package eu.blackserv.clientssh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileCloneTest {
    @Test
    fun clonePreservesConnectionDataAndChangesIdentity() {
        val source = HostProfile(
            id = "source-id",
            name = "franek",
            host = "blackserv.eu",
            port = 3399,
            username = "root",
            protocol = ConnectionProtocol.SSH,
            authenticationMethod = AuthenticationMethod.PRIVATE_KEY,
            password = "",
            privateKey = "private-key-material",
            privateKeyPassphrase = "passphrase",
        )

        val clone = cloneHostProfile(
            source = source,
            existingNames = listOf("franek"),
            newId = "clone-id",
        )

        assertEquals("clone-id", clone.id)
        assertNotEquals(source.id, clone.id)
        assertEquals("franek kopia", clone.name)
        assertEquals(source.host, clone.host)
        assertEquals(source.port, clone.port)
        assertEquals(source.username, clone.username)
        assertEquals(source.protocol, clone.protocol)
        assertEquals(source.authenticationMethod, clone.authenticationMethod)
        assertEquals(source.password, clone.password)
        assertEquals(source.privateKey, clone.privateKey)
        assertEquals(source.privateKeyPassphrase, clone.privateKeyPassphrase)
    }

    @Test
    fun cloneNameIsUniqueAndCaseInsensitive() {
        val name = nextCloneName(
            sourceName = "franek kopia",
            existingNames = listOf("franek", "FRANEK KOPIA", "franek kopia 2"),
        )

        assertEquals("franek kopia 3", name)
    }
}
