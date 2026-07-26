package eu.blackserv.clientssh.backup

import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.HostProfile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ProfileBackupException(message: String) : Exception(message)

object ProfileBackupCodec {
    private val containerMagic = "BSSHBK01".toByteArray(StandardCharsets.US_ASCII)
    private val payloadMagic = "PROFLS01".toByteArray(StandardCharsets.US_ASCII)

    private const val CONTAINER_VERSION = 1
    private const val PAYLOAD_VERSION = 1
    private const val KDF_ID = 1
    private const val KDF_ITERATIONS = 600_000
    private const val MIN_KDF_ITERATIONS = 100_000
    private const val MAX_KDF_ITERATIONS = 2_000_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8

    const val MIN_PASSWORD_CHARS = 16
    const val MAX_CONTAINER_BYTES = 8 * 1024 * 1024

    private const val MAX_PROFILES = 500
    private const val MAX_ID_BYTES = 128
    private const val MAX_NAME_BYTES = 512
    private const val MAX_HOST_BYTES = 1_024
    private const val MAX_USERNAME_BYTES = 512
    private const val MAX_PASSWORD_BYTES = 64 * 1024
    private const val MAX_PRIVATE_KEY_BYTES = 2 * 1024 * 1024
    private const val MAX_PASSPHRASE_BYTES = 64 * 1024
    private const val MAX_ENUM_BYTES = 64

    fun encrypt(
        profiles: List<HostProfile>,
        password: CharArray,
        secureRandom: SecureRandom = SecureRandom(),
    ): ByteArray {
        validatePassword(password)
        validateProfiles(profiles)

        val plaintext = encodePayload(profiles)
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val ciphertextLength = plaintext.size + GCM_TAG_BYTES
        val header = encodeHeader(salt, nonce, ciphertextLength)
        val keyBytes = deriveKey(password, salt, KDF_ITERATIONS)

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            if (ciphertext.size != ciphertextLength) {
                ciphertext.fill(0)
                throw ProfileBackupException("Nie udało się utworzyć kompletnego backupu.")
            }
            val result = ByteArrayOutputStream(header.size + ciphertext.size).use { output ->
                output.write(header)
                output.write(ciphertext)
                output.toByteArray()
            }
            ciphertext.fill(0)
            if (result.size > MAX_CONTAINER_BYTES) {
                result.fill(0)
                throw ProfileBackupException("Backup przekracza dozwolony rozmiar.")
            }
            return result
        } catch (error: ProfileBackupException) {
            throw error
        } catch (_: Throwable) {
            throw ProfileBackupException("Nie udało się zaszyfrować backupu.")
        } finally {
            plaintext.fill(0)
            keyBytes.fill(0)
            salt.fill(0)
            nonce.fill(0)
            header.fill(0)
        }
    }

    fun decrypt(container: ByteArray, password: CharArray): List<HostProfile> {
        validatePassword(password)
        if (container.isEmpty() || container.size > MAX_CONTAINER_BYTES) {
            throw ProfileBackupException("Nieprawidłowy rozmiar pliku backupu.")
        }

        val parsed = parseHeader(container)
        val keyBytes = deriveKey(password, parsed.salt, parsed.iterations)
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, parsed.nonce),
            )
            cipher.updateAAD(parsed.header)
            cipher.doFinal(container, parsed.ciphertextOffset, parsed.ciphertextLength)
        } catch (_: AEADBadTagException) {
            throw ProfileBackupException("Nieprawidłowe hasło albo uszkodzony backup.")
        } catch (_: Throwable) {
            throw ProfileBackupException("Nie udało się odszyfrować backupu.")
        } finally {
            keyBytes.fill(0)
            parsed.clear()
        }

        return try {
            decodePayload(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun encodeHeader(salt: ByteArray, nonce: ByteArray, ciphertextLength: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(containerMagic)
                data.writeInt(CONTAINER_VERSION)
                data.writeByte(KDF_ID)
                data.writeInt(KDF_ITERATIONS)
                data.writeByte(salt.size)
                data.write(salt)
                data.writeByte(nonce.size)
                data.write(nonce)
                data.writeInt(ciphertextLength)
            }
            output.toByteArray()
        }

    private data class ParsedHeader(
        val iterations: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertextOffset: Int,
        val ciphertextLength: Int,
        val header: ByteArray,
    ) {
        fun clear() {
            salt.fill(0)
            nonce.fill(0)
            header.fill(0)
        }
    }

    private fun parseHeader(container: ByteArray): ParsedHeader {
        try {
            val input = ByteArrayInputStream(container)
            val data = DataInputStream(input)
            val magic = ByteArray(containerMagic.size).also { data.readFully(it) }
            if (!magic.contentEquals(containerMagic)) fail("To nie jest backup Client SSH.")
            if (data.readInt() != CONTAINER_VERSION) fail("Nieobsługiwana wersja backupu.")
            if (data.readUnsignedByte() != KDF_ID) fail("Nieobsługiwany algorytm backupu.")

            val iterations = data.readInt()
            if (iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
                fail("Nieprawidłowe parametry KDF.")
            }
            val saltLength = data.readUnsignedByte()
            if (saltLength != SALT_BYTES) fail("Nieprawidłowa sól backupu.")
            val salt = ByteArray(saltLength).also { data.readFully(it) }
            val nonceLength = data.readUnsignedByte()
            if (nonceLength != NONCE_BYTES) fail("Nieprawidłowy nonce backupu.")
            val nonce = ByteArray(nonceLength).also { data.readFully(it) }
            val ciphertextLength = data.readInt()
            if (ciphertextLength !in GCM_TAG_BYTES..MAX_CONTAINER_BYTES) {
                fail("Nieprawidłowa długość zaszyfrowanych danych.")
            }
            val ciphertextOffset = container.size - input.available()
            if (input.available() != ciphertextLength) {
                fail("Backup jest obcięty albo zawiera nadmiarowe dane.")
            }
            return ParsedHeader(
                iterations = iterations,
                salt = salt,
                nonce = nonce,
                ciphertextOffset = ciphertextOffset,
                ciphertextLength = ciphertextLength,
                header = container.copyOfRange(0, ciphertextOffset),
            )
        } catch (error: ProfileBackupException) {
            throw error
        } catch (_: Throwable) {
            throw ProfileBackupException("Uszkodzony nagłówek backupu.")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } catch (_: Throwable) {
            throw ProfileBackupException("Nie udało się utworzyć klucza backupu.")
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodePayload(profiles: List<HostProfile>): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(payloadMagic)
                data.writeInt(PAYLOAD_VERSION)
                data.writeInt(profiles.size)
                profiles.forEach { profile ->
                    data.writeBounded(profile.id, MAX_ID_BYTES)
                    data.writeBounded(profile.name, MAX_NAME_BYTES)
                    data.writeBounded(profile.host, MAX_HOST_BYTES)
                    data.writeInt(profile.port)
                    data.writeBounded(profile.username, MAX_USERNAME_BYTES)
                    data.writeBounded(profile.protocol.name, MAX_ENUM_BYTES)
                    data.writeBounded(profile.authenticationMethod.name, MAX_ENUM_BYTES)
                    data.writeBounded(profile.password, MAX_PASSWORD_BYTES)
                    data.writeBounded(profile.privateKey, MAX_PRIVATE_KEY_BYTES)
                    data.writeBounded(profile.privateKeyPassphrase, MAX_PASSPHRASE_BYTES)
                }
            }
            output.toByteArray()
        }

    private fun decodePayload(plaintext: ByteArray): List<HostProfile> {
        try {
            val input = ByteArrayInputStream(plaintext)
            val data = DataInputStream(input)
            val magic = ByteArray(payloadMagic.size).also { data.readFully(it) }
            if (!magic.contentEquals(payloadMagic)) fail("Uszkodzona zawartość backupu.")
            if (data.readInt() != PAYLOAD_VERSION) fail("Nieobsługiwana wersja danych profili.")
            val count = data.readInt()
            if (count !in 0..MAX_PROFILES) fail("Nieprawidłowa liczba profili w backupie.")

            val profiles = ArrayList<HostProfile>(count)
            repeat(count) {
                profiles += HostProfile(
                    id = data.readBounded(MAX_ID_BYTES),
                    name = data.readBounded(MAX_NAME_BYTES),
                    host = data.readBounded(MAX_HOST_BYTES),
                    port = data.readInt(),
                    username = data.readBounded(MAX_USERNAME_BYTES),
                    protocol = enumStrict(data.readBounded(MAX_ENUM_BYTES)),
                    authenticationMethod = enumStrict(data.readBounded(MAX_ENUM_BYTES)),
                    password = data.readBounded(MAX_PASSWORD_BYTES),
                    privateKey = data.readBounded(MAX_PRIVATE_KEY_BYTES),
                    privateKeyPassphrase = data.readBounded(MAX_PASSPHRASE_BYTES),
                )
            }
            if (input.available() != 0) fail("Backup zawiera nadmiarowe dane profili.")
            validateProfiles(profiles)
            return profiles
        } catch (error: ProfileBackupException) {
            throw error
        } catch (_: Throwable) {
            throw ProfileBackupException("Uszkodzone dane profili w backupie.")
        }
    }

    private fun validatePassword(password: CharArray) {
        if (password.size < MIN_PASSWORD_CHARS) {
            fail("Hasło backupu musi mieć co najmniej $MIN_PASSWORD_CHARS znaków.")
        }
    }

    private fun validateProfiles(profiles: List<HostProfile>) {
        if (profiles.size > MAX_PROFILES) fail("Zbyt wiele profili w backupie.")
        val ids = HashSet<String>(profiles.size)
        profiles.forEach { profile ->
            requireField(profile.id, "identyfikator profilu", MAX_ID_BYTES)
            if (!ids.add(profile.id)) fail("Backup zawiera zduplikowane profile.")
            requireField(profile.name, "nazwa profilu", MAX_NAME_BYTES)
            requireField(profile.host, "host", MAX_HOST_BYTES)
            requireField(profile.username, "użytkownik", MAX_USERNAME_BYTES)
            if (profile.port !in 1..65_535) fail("Profil zawiera nieprawidłowy port.")
            requireLength(profile.password, "hasło SSH", MAX_PASSWORD_BYTES)
            requireLength(profile.privateKey, "klucz prywatny", MAX_PRIVATE_KEY_BYTES)
            requireLength(profile.privateKeyPassphrase, "passphrase", MAX_PASSPHRASE_BYTES)
            if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY && profile.privateKey.isBlank()) {
                fail("Profil klucza prywatnego nie zawiera klucza.")
            }
        }
    }

    private fun requireField(value: String, label: String, maxBytes: Int) {
        if (value.isBlank()) fail("Puste pole: $label.")
        requireLength(value, label, maxBytes)
    }

    private fun requireLength(value: String, label: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            if (bytes.size > maxBytes) fail("Pole '$label' przekracza dozwolony rozmiar.")
            if ('\u0000' in value) fail("Pole '$label' zawiera niedozwolony znak.")
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeBounded(value: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            if (bytes.size > maxBytes) fail("Pole backupu jest zbyt duże.")
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readBounded(maxBytes: Int): String {
        val length = readInt()
        if (length !in 0..maxBytes) fail("Nieprawidłowa długość pola backupu.")
        val bytes = ByteArray(length)
        return try {
            readFully(bytes)
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private inline fun <reified T : Enum<T>> enumStrict(raw: String): T =
        enumValues<T>().firstOrNull { it.name == raw }
            ?: throw ProfileBackupException("Backup zawiera nieznaną wartość konfiguracji.")

    private fun fail(message: String): Nothing = throw ProfileBackupException(message)
}
