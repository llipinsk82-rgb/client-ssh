package eu.blackserv.clientssh.backup

import eu.blackserv.clientssh.health.HealthMonitorConfig
import eu.blackserv.clientssh.model.AppSettings
import eu.blackserv.clientssh.model.AppSkin
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.FavoriteCommand
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.SshCompatibilityMode
import eu.blackserv.clientssh.model.TerminalSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable configuration backup. Fingerprints, history and terminal logs are intentionally excluded. */
data class ConfigurationBackupSnapshot(
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val profiles: List<HostProfile>,
    val favorites: List<FavoriteCommand>,
    val appSettings: AppSettings,
    val terminalSettings: TerminalSettings,
    val healthMonitorConfigs: List<HealthMonitorConfig>,
)

enum class ConfigurationImportMode {
    MERGE,
    REPLACE,
}

data class ConfigurationImportPlan(
    val profiles: List<HostProfile>,
    val favorites: List<FavoriteCommand>,
    val appSettings: AppSettings,
    val terminalSettings: TerminalSettings,
    val healthMonitorConfigs: List<HealthMonitorConfig>,
    val importedProfileCount: Int,
)

class ConfigurationBackupException(message: String) : IllegalArgumentException(message)

object ConfigurationBackupCodec {
    const val FILE_EXTENSION = "bssh"
    const val MAX_FILE_BYTES = 4 * 1024 * 1024
    const val MIN_EXPORT_PASSWORD_LENGTH = 10
    const val MAX_PASSWORD_LENGTH = 128

    private const val FILE_VERSION = 1
    private const val PAYLOAD_VERSION = 1
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_PROFILES = 500
    private const val MAX_FAVORITES = 500
    private const val MAX_MONITOR_CONFIGS = 500
    private const val MAX_ID_BYTES = 160
    private const val MAX_NAME_BYTES = 512
    private const val MAX_HOST_BYTES = 1_024
    private const val MAX_USERNAME_BYTES = 512
    private const val MAX_PASSWORD_BYTES = 16 * 1024
    private const val MAX_PRIVATE_KEY_BYTES = 256 * 1024
    private const val MAX_COMMAND_BYTES = 32 * 1024
    private const val MAX_PING_TARGET_BYTES = 1_024

    private val fileMagic = "BSSHBAK1".toByteArray(StandardCharsets.US_ASCII)
    private val payloadMagic = "BSSHPAY1".toByteArray(StandardCharsets.US_ASCII)
    private val aad = "ClientSSH|backup|v1|PBKDF2-HMAC-SHA256|AES-256-GCM"
        .toByteArray(StandardCharsets.US_ASCII)

    fun encrypt(snapshot: ConfigurationBackupSnapshot, password: CharArray): ByteArray {
        requireExportPassword(password)
        validateSnapshot(snapshot)

        val payload = encodePayload(snapshot)
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val ciphertext = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(aad)
                doFinal(payload)
            }
        } finally {
            payload.fill(0)
            key.encoded?.fill(0)
        }

        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(fileMagic)
                output.writeInt(FILE_VERSION)
                output.writeInt(PBKDF2_ITERATIONS)
                output.writeSizedBytes(salt)
                output.writeSizedBytes(iv)
                output.writeSizedBytes(ciphertext)
            }
            bytes.toByteArray()
        }.also {
            salt.fill(0)
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    fun decrypt(fileBytes: ByteArray, password: CharArray): ConfigurationBackupSnapshot {
        if (password.isEmpty() || password.size > MAX_PASSWORD_LENGTH) {
            throw ConfigurationBackupException("Nieprawidłowe hasło kopii.")
        }
        if (fileBytes.isEmpty() || fileBytes.size > MAX_FILE_BYTES) {
            throw ConfigurationBackupException("Plik kopii ma nieprawidłowy rozmiar.")
        }

        val envelope = parseEnvelope(fileBytes)
        val key = deriveKey(password, envelope.salt, envelope.iterations)
        val payload = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
                updateAAD(aad)
                doFinal(envelope.ciphertext)
            }
        } catch (_: AEADBadTagException) {
            throw ConfigurationBackupException("Nieprawidłowe hasło albo uszkodzony plik kopii.")
        } catch (_: Throwable) {
            throw ConfigurationBackupException("Nie można odszyfrować kopii konfiguracji.")
        } finally {
            key.encoded?.fill(0)
            envelope.clear()
        }

        return try {
            decodePayload(payload).also(::validateSnapshot)
        } finally {
            payload.fill(0)
        }
    }

    private fun encodePayload(snapshot: ConfigurationBackupSnapshot): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(payloadMagic)
                output.writeInt(PAYLOAD_VERSION)
                output.writeLong(snapshot.createdAtEpochMs)

                output.writeInt(snapshot.profiles.size)
                snapshot.profiles.forEach { profile ->
                    output.writeString(profile.id)
                    output.writeString(profile.name)
                    output.writeString(profile.host)
                    output.writeInt(profile.port)
                    output.writeString(profile.username)
                    output.writeString(profile.protocol.name)
                    output.writeString(profile.authenticationMethod.name)
                    output.writeString(profile.password)
                    output.writeString(profile.privateKey)
                    output.writeString(profile.privateKeyPassphrase)
                    output.writeString(profile.sshCompatibilityMode.name)
                }

                output.writeInt(snapshot.favorites.size)
                snapshot.favorites.forEach { favorite ->
                    output.writeString(favorite.id)
                    output.writeString(favorite.name)
                    output.writeString(favorite.command)
                    output.writeBoolean(favorite.runImmediately)
                }

                output.writeString(snapshot.appSettings.skin.canonical.name)
                output.writeBoolean(snapshot.terminalSettings.keepScreenAwake)
                output.writeBoolean(snapshot.terminalSettings.backgroundSessionEnabled)

                output.writeInt(snapshot.healthMonitorConfigs.size)
                snapshot.healthMonitorConfigs.forEach { config ->
                    output.writeString(config.profileId)
                    output.writeBoolean(config.enabled)
                    output.writeLong(config.intervalMinutes)
                    output.writeInt(config.timeoutMs)
                    output.writeInt(config.offlineFailureThreshold)
                    output.writeBoolean(config.sshTelemetryEnabled)
                    output.writeBoolean(config.pingEnabled)
                    output.writeString(config.pingTarget)
                }
            }
            bytes.toByteArray()
        }

    private fun decodePayload(payload: ByteArray): ConfigurationBackupSnapshot =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            input.requireMagic(payloadMagic, "Nieprawidłowy format danych kopii.")
            if (input.readInt() != PAYLOAD_VERSION) {
                throw ConfigurationBackupException("Ta wersja kopii nie jest obsługiwana.")
            }
            val createdAt = input.readLong().takeIf { it >= 0L }
                ?: throw ConfigurationBackupException("Nieprawidłowa data kopii.")

            val profiles = buildList {
                repeat(input.readCount(MAX_PROFILES, "profili")) {
                    add(
                        HostProfile(
                            id = input.readString(MAX_ID_BYTES),
                            name = input.readString(MAX_NAME_BYTES),
                            host = input.readString(MAX_HOST_BYTES),
                            port = input.readInt(),
                            username = input.readString(MAX_USERNAME_BYTES),
                            protocol = input.readEnum(),
                            authenticationMethod = input.readEnum(),
                            password = input.readString(MAX_PASSWORD_BYTES),
                            privateKey = input.readString(MAX_PRIVATE_KEY_BYTES),
                            privateKeyPassphrase = input.readString(MAX_PASSWORD_BYTES),
                            sshCompatibilityMode = input.readEnum(),
                        ),
                    )
                }
            }

            val favorites = buildList {
                repeat(input.readCount(MAX_FAVORITES, "poleceń ulubionych")) {
                    add(
                        FavoriteCommand(
                            id = input.readString(MAX_ID_BYTES),
                            name = input.readString(MAX_NAME_BYTES),
                            command = input.readString(MAX_COMMAND_BYTES),
                            runImmediately = input.readBoolean(),
                        ),
                    )
                }
            }

            val appSettings = AppSettings(skin = input.readEnum<AppSkin>().canonical)
            val terminalSettings = TerminalSettings(
                keepScreenAwake = input.readBoolean(),
                backgroundSessionEnabled = input.readBoolean(),
            )

            val monitorConfigs = buildList {
                repeat(input.readCount(MAX_MONITOR_CONFIGS, "ustawień Monitora")) {
                    add(
                        HealthMonitorConfig(
                            profileId = input.readString(MAX_ID_BYTES),
                            enabled = input.readBoolean(),
                            intervalMinutes = input.readLong(),
                            timeoutMs = input.readInt(),
                            offlineFailureThreshold = input.readInt(),
                            sshTelemetryEnabled = input.readBoolean(),
                            pingEnabled = input.readBoolean(),
                            pingTarget = input.readString(MAX_PING_TARGET_BYTES),
                        ),
                    )
                }
            }

            if (input.available() != 0) {
                throw ConfigurationBackupException("Plik kopii zawiera nadmiarowe dane.")
            }
            ConfigurationBackupSnapshot(
                createdAtEpochMs = createdAt,
                profiles = profiles,
                favorites = favorites,
                appSettings = appSettings,
                terminalSettings = terminalSettings,
                healthMonitorConfigs = monitorConfigs,
            )
        }

    private fun parseEnvelope(fileBytes: ByteArray): Envelope =
        DataInputStream(ByteArrayInputStream(fileBytes)).use { input ->
            input.requireMagic(fileMagic, "To nie jest kopia konfiguracji Client SSH.")
            if (input.readInt() != FILE_VERSION) {
                throw ConfigurationBackupException("Ta wersja pliku kopii nie jest obsługiwana.")
            }
            val iterations = input.readInt()
            if (iterations !in 100_000..1_000_000) {
                throw ConfigurationBackupException("Nieprawidłowe parametry zabezpieczenia kopii.")
            }
            val salt = input.readSizedBytes(SALT_BYTES, SALT_BYTES, "salt")
            val iv = input.readSizedBytes(IV_BYTES, IV_BYTES, "IV")
            val ciphertext = input.readSizedBytes(17, MAX_FILE_BYTES, "dane")
            if (input.available() != 0) {
                salt.fill(0)
                iv.fill(0)
                ciphertext.fill(0)
                throw ConfigurationBackupException("Plik kopii zawiera nadmiarowe dane.")
            }
            Envelope(iterations, salt, iv, ciphertext)
        }

    private fun validateSnapshot(snapshot: ConfigurationBackupSnapshot) {
        if (snapshot.createdAtEpochMs < 0L) fail("Nieprawidłowa data kopii.")
        if (snapshot.profiles.size > MAX_PROFILES) fail("Kopia zawiera zbyt wiele profili.")
        if (snapshot.favorites.size > MAX_FAVORITES) fail("Kopia zawiera zbyt wiele poleceń.")
        if (snapshot.healthMonitorConfigs.size > MAX_MONITOR_CONFIGS) {
            fail("Kopia zawiera zbyt wiele ustawień Monitora.")
        }

        val profileIds = mutableSetOf<String>()
        snapshot.profiles.forEach { profile ->
            requireBounded(profile.id, 1, MAX_ID_BYTES, "ID profilu")
            if (!profileIds.add(profile.id)) fail("Kopia zawiera powtórzone ID profilu.")
            requireBounded(profile.name, 1, MAX_NAME_BYTES, "nazwa profilu")
            requireBounded(profile.host.trim(), 1, MAX_HOST_BYTES, "host")
            requireBounded(profile.username.trim(), 1, MAX_USERNAME_BYTES, "użytkownik")
            if (profile.port !in 1..65_535) fail("Kopia zawiera nieprawidłowy port.")
            requireBounded(profile.password, 0, MAX_PASSWORD_BYTES, "hasło")
            requireBounded(profile.privateKey, 0, MAX_PRIVATE_KEY_BYTES, "klucz prywatny")
            requireBounded(profile.privateKeyPassphrase, 0, MAX_PASSWORD_BYTES, "passphrase")
        }

        val favoriteIds = mutableSetOf<String>()
        snapshot.favorites.forEach { favorite ->
            requireBounded(favorite.id, 1, MAX_ID_BYTES, "ID polecenia")
            if (!favoriteIds.add(favorite.id)) fail("Kopia zawiera powtórzone ID polecenia.")
            requireBounded(favorite.name, 1, MAX_NAME_BYTES, "nazwa polecenia")
            requireBounded(favorite.command, 1, MAX_COMMAND_BYTES, "polecenie")
        }

        val monitorIds = mutableSetOf<String>()
        snapshot.healthMonitorConfigs.forEach { config ->
            if (config.profileId !in profileIds) fail("Ustawienie Monitora nie pasuje do profilu.")
            if (!monitorIds.add(config.profileId)) fail("Powtórzone ustawienie Monitora.")
            requireBounded(config.pingTarget, 1, MAX_PING_TARGET_BYTES, "cel ping")
        }
    }

    private fun requireExportPassword(password: CharArray) {
        if (password.size !in MIN_EXPORT_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            throw ConfigurationBackupException(
                "Hasło kopii musi mieć od $MIN_EXPORT_PASSWORD_LENGTH do $MAX_PASSWORD_LENGTH znaków.",
            )
        }
    }

    private fun requireBounded(value: String, minBytes: Int, maxBytes: Int, label: String) {
        val size = value.toByteArray(StandardCharsets.UTF_8).size
        if (size !in minBytes..maxBytes) fail("Nieprawidłowe pole: $label.")
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun DataOutputStream.writeSizedBytes(value: ByteArray) {
        writeInt(value.size)
        write(value)
    }

    private fun DataOutputStream.writeString(value: String) {
        writeSizedBytes(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun DataInputStream.readSizedBytes(min: Int, max: Int, label: String): ByteArray {
        val size = runCatching { readInt() }.getOrElse { fail("Uszkodzone pole: $label.") }
        if (size !in min..max || size > available()) fail("Uszkodzone pole: $label.")
        return ByteArray(size).also(::readFully)
    }

    private fun DataInputStream.readString(maxBytes: Int): String {
        val bytes = readSizedBytes(0, maxBytes, "tekst")
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Throwable) {
            fail("Kopia zawiera nieprawidłowy tekst.")
        } finally {
            bytes.fill(0)
        }
    }

    private inline fun <reified T : Enum<T>> DataInputStream.readEnum(): T {
        val raw = readString(128)
        return runCatching { enumValueOf<T>(raw) }
            .getOrElse { fail("Kopia zawiera nieobsługiwaną wartość.") }
    }

    private fun DataInputStream.readCount(max: Int, label: String): Int {
        val count = runCatching { readInt() }.getOrElse { fail("Brak liczby $label.") }
        if (count !in 0..max) fail("Nieprawidłowa liczba $label.")
        return count
    }

    private fun DataInputStream.requireMagic(expected: ByteArray, message: String) {
        val actual = ByteArray(expected.size)
        runCatching { readFully(actual) }.getOrElse { fail(message) }
        if (!actual.contentEquals(expected)) fail(message)
    }

    private fun fail(message: String): Nothing = throw ConfigurationBackupException(message)

    private data class Envelope(
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        fun clear() {
            salt.fill(0)
            iv.fill(0)
            ciphertext.fill(0)
        }
    }
}

fun planConfigurationImport(
    current: ConfigurationBackupSnapshot,
    imported: ConfigurationBackupSnapshot,
    mode: ConfigurationImportMode,
): ConfigurationImportPlan {
    if (mode == ConfigurationImportMode.REPLACE) {
        return ConfigurationImportPlan(
            profiles = imported.profiles,
            favorites = imported.favorites,
            appSettings = imported.appSettings.copy(skin = imported.appSettings.skin.canonical),
            terminalSettings = imported.terminalSettings,
            healthMonitorConfigs = imported.healthMonitorConfigs,
            importedProfileCount = imported.profiles.size,
        )
    }

    val mergedProfiles = current.profiles.toMutableList()
    val importedIdToFinalId = mutableMapOf<String, String>()
    imported.profiles.forEach { incoming ->
        val byId = mergedProfiles.indexOfFirst { it.id == incoming.id }
        val byEndpoint = mergedProfiles.indexOfFirst { it.endpointIdentity() == incoming.endpointIdentity() }
        val index = if (byId >= 0) byId else byEndpoint
        if (index >= 0) {
            val finalId = mergedProfiles[index].id
            mergedProfiles[index] = incoming.copy(id = finalId)
            importedIdToFinalId[incoming.id] = finalId
        } else {
            mergedProfiles += incoming
            importedIdToFinalId[incoming.id] = incoming.id
        }
    }

    val mergedFavorites = current.favorites.toMutableList()
    imported.favorites.forEach { incoming ->
        val byId = mergedFavorites.indexOfFirst { it.id == incoming.id }
        val byContent = mergedFavorites.indexOfFirst {
            it.name.equals(incoming.name, ignoreCase = true) && it.command == incoming.command
        }
        val index = if (byId >= 0) byId else byContent
        if (index >= 0) {
            mergedFavorites[index] = incoming.copy(id = mergedFavorites[index].id)
        } else {
            mergedFavorites += incoming
        }
    }

    val mergedMonitor = current.healthMonitorConfigs.associateBy { it.profileId }.toMutableMap()
    imported.healthMonitorConfigs.forEach { incoming ->
        val finalId = importedIdToFinalId[incoming.profileId] ?: incoming.profileId
        mergedMonitor[finalId] = incoming.copy(profileId = finalId)
    }

    return ConfigurationImportPlan(
        profiles = mergedProfiles,
        favorites = mergedFavorites,
        appSettings = imported.appSettings.copy(skin = imported.appSettings.skin.canonical),
        terminalSettings = imported.terminalSettings,
        healthMonitorConfigs = mergedMonitor.values.sortedBy { it.profileId },
        importedProfileCount = imported.profiles.size,
    )
}

private fun HostProfile.endpointIdentity(): String = listOf(
    protocol.name,
    host.trim().lowercase(),
    port.toString(),
    username.trim().lowercase(),
).joinToString("|")
