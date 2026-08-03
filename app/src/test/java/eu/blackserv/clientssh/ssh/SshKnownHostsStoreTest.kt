package eu.blackserv.clientssh.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SshKnownHostsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `creates empty verified repository when no legacy file exists`() {
        val directory = temporaryFolder.newFolder("ssh")

        val active = SshKnownHostsStore.prepareDirectory(directory)

        assertTrue(active.isFile)
        assertEquals(0L, active.length())
        assertTrue(directory.resolve(".explicit-host-key-verification-v1").isFile)
        assertTrue(directory.resolve(".host-key-port-scope-v2").isFile)
    }

    @Test
    fun `archives accept-new entries and clears active repository`() {
        val directory = temporaryFolder.newFolder("ssh")
        val active = directory.resolve("known_hosts")
        active.writeText("example.test ssh-ed25519 AAAATEST\n")

        val prepared = SshKnownHostsStore.prepareDirectory(directory)

        assertEquals(0L, prepared.length())
        assertEquals(
            "example.test ssh-ed25519 AAAATEST\n",
            directory.resolve("known_hosts.accept-new-unverified").readText(),
        )
    }

    @Test
    fun `does not clear explicitly verified entries on later starts`() {
        val directory = temporaryFolder.newFolder("ssh")
        val active = SshKnownHostsStore.prepareDirectory(directory)
        active.writeText("verified.test ssh-ed25519 AAAAVERIFIED\n")

        val preparedAgain = SshKnownHostsStore.prepareDirectory(directory)

        assertEquals("verified.test ssh-ed25519 AAAAVERIFIED\n", preparedAgain.readText())
        assertFalse(directory.resolve("known_hosts.accept-new-unverified").exists())
    }

    @Test
    fun `uses a new backup name instead of overwriting prior audit copy`() {
        val directory = temporaryFolder.newFolder("ssh")
        directory.resolve("known_hosts.accept-new-unverified").writeText("older\n")
        directory.resolve("known_hosts").writeText("newer\n")

        SshKnownHostsStore.prepareDirectory(directory)

        assertEquals("older\n", directory.resolve("known_hosts.accept-new-unverified").readText())
        assertEquals("newer\n", directory.resolve("known_hosts.accept-new-unverified.2").readText())
    }

    @Test
    fun `upgrades v1 repository by archiving ambiguous host-only entries`() {
        val directory = temporaryFolder.newFolder("ssh")
        directory.resolve(".explicit-host-key-verification-v1").writeText("v1\n")
        directory.resolve("known_hosts").writeText("blackserv.eu ssh-ed25519 AAAAOLD\n")

        val prepared = SshKnownHostsStore.prepareDirectory(directory)

        assertEquals(0L, prepared.length())
        assertEquals(
            "blackserv.eu ssh-ed25519 AAAAOLD\n",
            directory.resolve("known_hosts.pre-port-scope-v2").readText(),
        )
        assertTrue(directory.resolve(".host-key-port-scope-v2").isFile)
    }

    @Test
    fun `known hosts directory in place of file is rejected`() {
        val directory = temporaryFolder.newFolder("ssh")
        directory.resolve("known_hosts").mkdir()

        assertThrows(IllegalStateException::class.java) {
            SshKnownHostsStore.prepareDirectory(directory)
        }
    }

    @Test
    fun `migration marker directory is rejected`() {
        val directory = temporaryFolder.newFolder("ssh")
        directory.resolve(".explicit-host-key-verification-v1").mkdir()

        assertThrows(IllegalStateException::class.java) {
            SshKnownHostsStore.prepareDirectory(directory)
        }
    }
}
