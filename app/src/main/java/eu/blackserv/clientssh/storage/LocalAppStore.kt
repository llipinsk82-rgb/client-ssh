package eu.blackserv.clientssh.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.ConnectionProtocol
import eu.blackserv.clientssh.model.FavoriteCommand
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.TerminalSettings
import eu.blackserv.clientssh.model.defaultFavoriteCommands
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

class LocalAppStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProfiles(): List<HostProfile> =
        loadProfilesFrom(KEY_PROFILES).ifEmpty { loadProfilesFrom(KEY_PROFILES_BACKUP) }

    private fun loadProfilesFrom(key: String): List<HostProfile> {
        val raw = prefs.getString(key, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching { item.toHostProfile() }
                    .getOrNull()
                    ?.let { add(it) }
            }
        }
    }

    fun saveProfiles(profiles: List<HostProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            runCatching { array.put(profile.toJson()) }
        }
        val raw = array.toString()
        prefs.edit()
            .putString(KEY_PROFILES, raw)
            .putString(KEY_PROFILES_BACKUP, raw)
            .commit()
    }

    fun loadFavorites(): List<FavoriteCommand> {
        if (!prefs.contains(KEY_FAVORITES)) return defaultFavoriteCommands()
        val raw = prefs.getString(KEY_FAVORITES, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return defaultFavoriteCommands()
        val loaded = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching { item.toFavoriteCommand() }
                    .getOrNull()
                    ?.let { add(it) }
            }
        }
        return loaded.ifEmpty { defaultFavoriteCommands() }
    }

    fun saveFavorites(favorites: List<FavoriteCommand>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            runCatching { array.put(favorite.toJson()) }
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).commit()
    }

    fun loadTerminalSettings(): TerminalSettings = TerminalSettings(
        keepScreenAwake = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true),
    )

    fun saveTerminalSettings(settings: TerminalSettings) {
        prefs.edit()
            .putBoolean(KEY_KEEP_SCREEN_AWAKE, settings.keepScreenAwake)
            .commit()
    }

    private fun HostProfile.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host.trim())
        .put("port", port)
        .put("username", username.trim())
        .put("protocol", protocol.name)
        .put("authenticationMethod", authenticationMethod.name)
        .put("password", encryptOrBlank(password))
        .put("privateKey", encryptOrBlank(privateKey))
        .put("privateKeyPassphrase", encryptOrBlank(privateKeyPassphrase))

    private fun JSONObject.toHostProfile(): HostProfile = HostProfile(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        name = optString("name").trim(),
        host = optString("host").trim(),
        port = optInt("port", ConnectionProtocol.SSH.defaultPort),
        username = optString("username").trim(),
        protocol = enumValueOrDefault(optString("protocol"), ConnectionProtocol.SSH),
        authenticationMethod = enumValueOrDefault(optString("authenticationMethod"), AuthenticationMethod.PASSWORD),
        password = decryptOrBlank(optString("password")),
        privateKey = decryptOrBlank(optString("privateKey")),
        privateKeyPassphrase = decryptOrBlank(optString("privateKeyPassphrase")),
    )

    private fun FavoriteCommand.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name.trim())
        .put("command", command)
        .put("runImmediately", runImmediately)

    private fun JSONObject.toFavoriteCommand(): FavoriteCommand = FavoriteCommand(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        name = optString("name").trim(),
        command = optString("command"),
        runImmediately = optBoolean("runImmediately", false),
    )

    private fun encryptOrBlank(value: String): String =
        if (value.isBlank()) "" else runCatching { encrypt(value) }.getOrDefault("")

    private fun decryptOrBlank(value: String): String =
        if (value.isBlank()) "" else runCatching { decrypt(value) }.getOrDefault("")

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return base64(iv) + ":" + base64(encrypted)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Nieprawidłowy zapis sekretu." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)

    companion object {
        private const val PREFS_NAME = "client_ssh_store"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_PROFILES_BACKUP = "profiles_backup"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_ALIAS = "blackserv-client-ssh-secrets"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
