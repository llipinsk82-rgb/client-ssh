package eu.blackserv.clientssh.backup

import eu.blackserv.clientssh.health.HealthMonitorConfig
import eu.blackserv.clientssh.model.AppSettings
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.FavoriteCommand
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.TerminalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigurationBackupTest {
    @Test
    fun `encrypted backup round trip preserves configuration and secrets`() {
        val source = sampleSnapshot()
        val password = "correct horse battery".toCharArray()

        val encrypted = ConfigurationBackupCodec.encrypt(source, password)
        val restored = ConfigurationBackupCodec.decrypt(encrypted, password)

        assertEquals(source, restored)
        assertTrue(encrypted.size <= ConfigurationBackupCodec.MAX_FILE_BYTES)
    }

    @Test
    fun `backup never contains plaintext credentials or private key`() {
        val source = sampleSnapshot()
        val encrypted = ConfigurationBackupCodec.encrypt(source, "a-strong-backup-password".toCharArray())
        val raw = encrypted.toString(Charsets.ISO_8859_1)

        assertFalse(raw.contains("super-secret-password"))
        assertFalse(raw.contains("BEGIN OPENSSH PRIVATE KEY"))
        assertFalse(raw.contains("blackserv.eu"))
        assertFalse(raw.contains("debian"))
    }

    @Test
    fun `wrong password and tampering are rejected`() {
        val encrypted = ConfigurationBackupCodec.encrypt(
            sampleSnapshot(),
            "a-strong-backup-password".toCharArray(),
        )

        assertBackupRejected {
            ConfigurationBackupCodec.decrypt(encrypted, "wrong-password-value".toCharArray())
        }

        val tampered = encrypted.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }
        assertBackupRejected {
            ConfigurationBackupCodec.decrypt(tampered, "a-strong-backup-password".toCharArray())
        }
    }

    @Test
    fun `merge updates matching endpoint and remaps monitor profile id`() {
        val currentProfile = sampleSnapshot().profiles.single().copy(
            id = "current-id",
            name = "Current",
            password = "old-password",
        )
        val importedProfile = sampleSnapshot().profiles.single().copy(
            id = "backup-id",
            name = "Imported",
            password = "new-password",
        )
        val current = sampleSnapshot().copy(
            profiles = listOf(currentProfile),
            healthMonitorConfigs = listOf(HealthMonitorConfig(profileId = currentProfile.id)),
        )
        val imported = sampleSnapshot().copy(
            profiles = listOf(importedProfile),
            healthMonitorConfigs = listOf(
                HealthMonitorConfig(
                    profileId = importedProfile.id,
                    enabled = true,
                    sshTelemetryEnabled = true,
                ),
            ),
        )

        val plan = planConfigurationImport(current, imported, ConfigurationImportMode.MERGE)

        assertEquals(1, plan.profiles.size)
        assertEquals("current-id", plan.profiles.single().id)
        assertEquals("Imported", plan.profiles.single().name)
        assertEquals("new-password", plan.profiles.single().password)
        assertEquals("current-id", plan.healthMonitorConfigs.single().profileId)
        assertTrue(plan.healthMonitorConfigs.single().enabled)
    }

    @Test
    fun `replace uses backup as exact source of truth`() {
        val imported = sampleSnapshot()
        val current = sampleSnapshot().copy(
            profiles = listOf(sampleSnapshot().profiles.single().copy(id = "other", port = 2200)),
        )

        val plan = planConfigurationImport(current, imported, ConfigurationImportMode.REPLACE)

        assertEquals(imported.profiles, plan.profiles)
        assertEquals(imported.favorites, plan.favorites)
        assertEquals(imported.healthMonitorConfigs, plan.healthMonitorConfigs)
    }

    private fun sampleSnapshot(): ConfigurationBackupSnapshot {
        val profile = HostProfile(
            id = "profile-1",
            name = "OS1",
            host = "blackserv.eu",
            port = 3377,
            username = "debian",
            protocol = ConnectionProtocol.SSH,
            authenticationMethod = AuthenticationMethod.PRIVATE_KEY,
            password = "super-secret-password",
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret-material\n-----END OPENSSH PRIVATE KEY-----",
            privateKeyPassphrase = "private-key-passphrase",
        )
        return ConfigurationBackupSnapshot(
            createdAtEpochMs = 1_700_000_000_000L,
            profiles = listOf(profile),
            favorites = listOf(
                FavoriteCommand(
                    id = "favorite-1",
                    name = "status",
                    command = "systemctl status ssh",
                    runImmediately = true,
                ),
            ),
            appSettings = AppSettings(AppSkin.OBSIDIAN),
            terminalSettings = TerminalSettings(
                keepScreenAwake = false,
                backgroundSessionEnabled = true,
            ),
            healthMonitorConfigs = listOf(
                HealthMonitorConfig(
                    profileId = profile.id,
                    enabled = true,
                    intervalMinutes = 30,
                    sshTelemetryEnabled = true,
                    pingEnabled = true,
                    pingTarget = "1.1.1.1",
                ),
            ),
        )
    }

    private fun assertBackupRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected backup rejection")
        } catch (_: ConfigurationBackupException) {
            // expected
        }
    }
}
