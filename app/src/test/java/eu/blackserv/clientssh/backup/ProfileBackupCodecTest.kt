package eu.blackserv.clientssh.backup

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileBackupCodecTest {
    private val password = "correct-horse-battery-staple".toCharArray()

    @Test
    fun `round trip preserves all profile authentication modes`() {
        val profiles = listOf(
            HostProfile(
                id = "password-profile",
                name = "Serwer główny",
                host = "ssh.example.test",
                port = 22,
                username = "admin",
                protocol = ConnectionProtocol.SSH,
                authenticationMethod = AuthenticationMethod.PASSWORD,
                password = "ssh-password",
            ),
            HostProfile(
                id = "key-profile",
                name = "Klucz Ed25519",
                host = "10.0.0.10",
                port = 2222,
                username = "root",
                protocol = ConnectionProtocol.SSH,
                authenticationMethod = AuthenticationMethod.PRIVATE_KEY,
                privateKey = "private-key-test-material",
                privateKeyPassphrase = "key-passphrase",
            ),
            HostProfile(
                id = "interactive-profile",
                name = "Interaktywny",
                host = "interactive.example.test",
                port = 22,
                username = "operator",
                protocol = ConnectionProtocol.SSH,
                authenticationMethod = AuthenticationMethod.INTERACTIVE,
            ),
        )

        val encrypted = ProfileBackupCodec.encrypt(profiles, password.copyOf())
        val restored = ProfileBackupCodec.decrypt(encrypted, password.copyOf())

        assertEquals(profiles, restored)
    }

    @Test
    fun `container never contains recognizable profile secrets`() {
        val secretKey = "unique-sensitive-key-material-82"
        val secretPassword = "unique-ssh-password-91"
        val profile = validKeyProfile().copy(
            privateKey = secretKey,
            privateKeyPassphrase = secretPassword,
        )

        val encrypted = ProfileBackupCodec.encrypt(listOf(profile), password.copyOf())
        val raw = String(encrypted, StandardCharsets.ISO_8859_1)

        assertFalse(raw.contains(secretKey))
        assertFalse(raw.contains(secretPassword))
    }

    @Test
    fun `wrong password changes nothing and returns sanitized error`() {
        val encrypted = ProfileBackupCodec.encrypt(listOf(validKeyProfile()), password.copyOf())

        val error = expectBackupFailure {
            ProfileBackupCodec.decrypt(encrypted, "wrong-password-with-enough-length".toCharArray())
        }

        assertTrue(error.message.orEmpty().contains("Nieprawidłowe hasło"))
        assertFalse(error.message.orEmpty().contains("wrong-password"))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val encrypted = ProfileBackupCodec.encrypt(listOf(validKeyProfile()), password.copyOf())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        expectBackupFailure { ProfileBackupCodec.decrypt(encrypted, password.copyOf()) }
    }

    @Test
    fun `truncated backup is rejected before import`() {
        val encrypted = ProfileBackupCodec.encrypt(listOf(validKeyProfile()), password.copyOf())
        val truncated = encrypted.copyOf(encrypted.size - 5)

        expectBackupFailure { ProfileBackupCodec.decrypt(truncated, password.copyOf()) }
    }

    @Test
    fun `duplicate profile ids are rejected before encryption`() {
        val first = validKeyProfile()
        val second = first.copy(name = "Duplikat")

        val error = expectBackupFailure {
            ProfileBackupCodec.encrypt(listOf(first, second), password.copyOf())
        }

        assertTrue(error.message.orEmpty().contains("zduplikowane"))
    }

    @Test
    fun `invalid port is rejected before encryption`() {
        val error = expectBackupFailure {
            ProfileBackupCodec.encrypt(listOf(validKeyProfile().copy(port = 0)), password.copyOf())
        }

        assertTrue(error.message.orEmpty().contains("port"))
    }

    @Test
    fun `short backup password is rejected`() {
        val error = expectBackupFailure {
            ProfileBackupCodec.encrypt(listOf(validKeyProfile()), "too-short".toCharArray())
        }

        assertTrue(error.message.orEmpty().contains(ProfileBackupCodec.MIN_PASSWORD_CHARS.toString()))
    }

    @Test
    fun `excessively long backup password is rejected`() {
        val error = expectBackupFailure {
            ProfileBackupCodec.encrypt(
                listOf(validKeyProfile()),
                CharArray(ProfileBackupCodec.MAX_PASSWORD_CHARS + 1) { 'x' },
            )
        }

        assertTrue(error.message.orEmpty().contains(ProfileBackupCodec.MAX_PASSWORD_CHARS.toString()))
    }

    @Test
    fun `aggregate plaintext limit is enforced before encryption`() {
        val largeKey = "k".repeat(2 * 1024 * 1024)
        val profiles = (1..4).map { index ->
            validKeyProfile().copy(id = "key-profile-$index", privateKey = largeKey)
        }

        val error = expectBackupFailure {
            ProfileBackupCodec.encrypt(profiles, password.copyOf())
        }

        assertTrue(error.message.orEmpty().contains("rozmiar"))
    }

    @Test
    fun `trailing bytes are rejected`() {
        val encrypted = ProfileBackupCodec.encrypt(listOf(validKeyProfile()), password.copyOf())
        val extended = encrypted + byteArrayOf(1, 2, 3)

        expectBackupFailure { ProfileBackupCodec.decrypt(extended, password.copyOf()) }
    }

    private fun validKeyProfile() = HostProfile(
        id = "key-profile",
        name = "Klucz",
        host = "example.test",
        port = 22,
        username = "root",
        protocol = ConnectionProtocol.SSH,
        authenticationMethod = AuthenticationMethod.PRIVATE_KEY,
        privateKey = "private-key-test-material",
        privateKeyPassphrase = "passphrase",
    )

    private fun expectBackupFailure(block: () -> Unit): ProfileBackupException {
        try {
            block()
            fail("Oczekiwano ProfileBackupException")
        } catch (error: ProfileBackupException) {
            return error
        }
        error("unreachable")
    }
}
