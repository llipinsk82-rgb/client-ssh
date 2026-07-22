package eu.blackserv.clientssh.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import eu.blackserv.clientssh.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSha256: String?,
    val releaseUrl: String,
)

sealed interface UpdateCheckResult {
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    data object Current : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface InstallLaunchResult {
    data object Started : InstallLaunchResult
    data object PermissionRequired : InstallLaunchResult
    data class Error(val message: String) : InstallLaunchResult
}

class GitHubUpdateManager(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    fun check(onResult: (UpdateCheckResult) -> Unit) {
        executor.execute {
            val result = runCatching { fetchNewestAvailableRelease() }
                .fold(
                    onSuccess = { release ->
                        if (release != null) UpdateCheckResult.Available(release) else UpdateCheckResult.Current
                    },
                    onFailure = {
                        UpdateCheckResult.Error(it.message ?: "Nie udało się sprawdzić aktualizacji")
                    },
                )
            post { onResult(result) }
        }
    }

    fun download(release: ReleaseInfo, onResult: (Result<File>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(directory, release.apkName.ifBlank { "client-ssh-${release.version}.apk" })
                val partial = File(directory, "${target.name}.part")
                if (partial.exists()) partial.delete()
                if (target.exists()) target.delete()

                downloadToFile(release.apkUrl, partial)
                release.apkSha256?.let { expected -> verifySha256(partial, expected) }
                partial.renameTo(target) || error("Nie udało się przygotować pliku APK do instalacji.")
                target
            }
            post { onResult(result) }
        }
    }

    fun install(apk: File): InstallLaunchResult = runCatching {
        if (!apk.exists() || apk.length() <= 0L) {
            return InstallLaunchResult.Error("Plik APK nie istnieje albo jest pusty. Pobierz aktualizację ponownie.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallLaunchResult.PermissionRequired
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        InstallLaunchResult.Started
    }.getOrElse { InstallLaunchResult.Error(it.message ?: "Nie udało się uruchomić instalatora Androida.") }

    private fun fetchNewestAvailableRelease(): ReleaseInfo? {
        val connection = openConnection(RELEASES_URL, api = true)
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val releases = JSONArray(response)

        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue

            val tag = release.optString("tag_name")
            val version = tag.removePrefix("v").removePrefix("V")
            if (!isNewer(version, BuildConfig.VERSION_NAME)) continue

            val assets = release.optJSONArray("assets") ?: continue
            val apkAsset = findApkAsset(assets) ?: continue
            val shaAsset = findShaAsset(assets, apkAsset.optString("name"))

            return ReleaseInfo(
                tag = tag,
                version = version,
                notes = release.optString("body").ifBlank { "Brak opisu zmian." },
                apkName = apkAsset.getString("name"),
                apkUrl = apkAsset.getString("browser_download_url"),
                apkSha256 = shaAsset?.let { fetchSha256(it.getString("browser_download_url")) },
                releaseUrl = release.optString("html_url"),
            )
        }

        return null
    }

    private fun findApkAsset(assets: JSONArray): JSONObject? = (0 until assets.length())
        .asSequence()
        .map { assets.getJSONObject(it) }
        .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        .sortedWith(
            compareByDescending<JSONObject> { !it.optString("name").contains("debug", ignoreCase = true) }
                .thenBy { it.optString("name") },
        )
        .firstOrNull()

    private fun findShaAsset(assets: JSONArray, apkName: String): JSONObject? = (0 until assets.length())
        .asSequence()
        .map { assets.getJSONObject(it) }
        .firstOrNull { it.optString("name").equals("$apkName.sha256", ignoreCase = true) }
        ?: (0 until assets.length())
            .asSequence()
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".sha256", ignoreCase = true) }

    private fun fetchSha256(url: String): String? {
        val text = openConnection(url, api = false).inputStream.bufferedReader().use { it.readText() }
        return SHA256_REGEX.find(text)?.value?.lowercase()
    }

    private fun downloadToFile(url: String, target: File) {
        openConnection(url, api = false).inputStream.use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        require(target.length() > 0L) { "Pobrany plik APK jest pusty." }
    }

    private fun verifySha256(file: File, expected: String) {
        val actual = sha256(file)
        require(actual.equals(expected, ignoreCase = true)) {
            "Pobrany APK ma nieprawidłową sumę SHA-256. Pobierz aktualizację ponownie."
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun openConnection(url: String, api: Boolean): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Client-SSH/${BuildConfig.VERSION_NAME}")
            if (api) {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            } else {
                setRequestProperty("Accept", "application/octet-stream")
            }
        }

        val status = connection.responseCode
        if (status !in 200..299) {
            val message = when (status) {
                404 -> "Na GitHub nie ma jeszcze opublikowanego wydania Client SSH."
                403 -> "GitHub zablokował chwilowo sprawdzanie aktualizacji. Spróbuj później."
                else -> "GitHub zwrócił błąd HTTP $status."
            }
            connection.disconnect()
            error(message)
        }
        return connection
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = versionParts(remote)
        val localParts = versionParts(local)
        val length = maxOf(remoteParts.size, localParts.size)
        return (0 until length).firstNotNullOfOrNull { index ->
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            when {
                remotePart > localPart -> true
                remotePart < localPart -> false
                else -> null
            }
        } ?: false
    }

    private fun versionParts(version: String): List<Int> = version
        .substringBefore('-')
        .split('.')
        .map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }

    private fun post(block: () -> Unit) {
        android.os.Handler(context.mainLooper).post(block)
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/llipinsk82-rgb/client-ssh/releases?per_page=10"
        private val SHA256_REGEX = Regex("[a-fA-F0-9]{64}")
    }
}
