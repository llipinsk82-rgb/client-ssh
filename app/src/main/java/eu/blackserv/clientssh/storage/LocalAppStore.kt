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
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

class LocalAppStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProfiles(): List<HostProfile> = runCatching {
        val raw = prefs.getString(KEY_PROFILES, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(item.toHostProfile())
            }
        }
    }.getOrElse { emptyList() }

    fun saveProfiles(profiles: List<HostProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun loadFavorites(): List<FavoriteCommand> = runCatching {
        if (!prefs.contains(KEY_FAVORITES)) return@runCatching defaultFavoriteCommands()
        val raw = prefs.getString(KEY_FAVORITES, "[]").orEmpty()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(item.toFavoriteCommand())
            }
        }
    }.getOrElse { defaultFavoriteCommands() }

    fun saveFavorites(favorites: List<FavoriteCommand>) {
        val array = JSONArray()
        favorites.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    fun loadTerminalSettings(): TerminalSettings = TerminalSettings(
        keepScreenAwake = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true),
    )

    fun saveTerminalSettings(settings: TerminalSettings) {
        prefs.edit()
            .putBoolean(KEY_KEEP_SCREEN_AWAKE, settings.keepScreenAwake)
            .apply()
    }

    private fun HostProfile.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host)
        .put("port", port)
        .put("username", username)
        .put("protocol", protocol.name)
        .put("authenticationMethod", authenticationMethod.name)
        .put("password", encryptOrBlank(password))
        .put("privateKey", encryptOrBlank(privateKey))
        .put("privateKeyPassphrase", encryptOrBlank(privateKeyPassphrase))

    private fun JSONObject.toHostProfile(): HostProfile = HostProfile(
        id = optString("id"),
        name = optString("name"),
        host = optString("host"),
        port = optInt("port", ConnectionProtocol.SSH.defaultPort),
        username = optString("username"),
        protocol = enumValueOrDefault(optString("protocol"), ConnectionProtocol.SSH),
        authenticationMethod = enumValueOrDefault(optString("authenticationMethod"), AuthenticationMethod.PASSWORD),
        password = decryptOrBlank(optString("password")),
        privateKey = decryptOrBlank(optString("privateKey")),
        privateKeyPassphrase = decryptOrBlank(optString("privateKeyPassphrase")),
    )

    private fun FavoriteCommand.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("command", command)
        .put("runImmediately", runImmediately)

    private fun JSONObject.toFavoriteCommand(): FavoriteCommand = FavoriteCommand(
        id = optString("id"),
        name = optString("name"),
        command = optString("command"),
        runImmediately = optBoolean("runImmediately", false),
    )

    private fun encryptOrBlank(value: String): String =
        if (value.isBlank()) "" else encrypt(value)

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
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_ALIAS = "blackserv-client-ssh-secrets"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
