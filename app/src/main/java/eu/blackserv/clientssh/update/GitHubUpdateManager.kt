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
import java.util.concurrent.Executors
import org.json.JSONObject

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
)

sealed interface UpdateCheckResult {
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    data object Current : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class GitHubUpdateManager(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()

    fun check(onResult: (UpdateCheckResult) -> Unit) {
        executor.execute {
            val result = runCatching { fetchLatestRelease() }
                .fold(
                    onSuccess = { release ->
                        if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
                            UpdateCheckResult.Available(release)
                        } else {
                            UpdateCheckResult.Current
                        }
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
                downloadToFile(release.apkUrl, target)
                target
            }
            post { onResult(result) }
        }
    }

    fun install(apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
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
        return true
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val connection = openConnection(LATEST_RELEASE_URL)
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val assets = json.getJSONArray("assets")
        val apkAsset = (0 until assets.length())
            .asSequence()
            .map { assets.getJSONObject(it) }
            .firstOrNull { asset ->
                asset.optString("name").endsWith(".apk", ignoreCase = true)
            } ?: error("Najnowsze wydanie GitHub nie zawiera pliku APK.")

        val tag = json.getString("tag_name")
        return ReleaseInfo(
            tag = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            notes = json.optString("body").ifBlank { "Brak opisu zmian." },
            apkName = apkAsset.getString("name"),
            apkUrl = apkAsset.getString("browser_download_url"),
        )
    }

    private fun downloadToFile(url: String, target: File) {
        openConnection(url).inputStream.use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        require(target.length() > 0L) { "Pobrany plik APK jest pusty." }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Client-SSH/${BuildConfig.VERSION_NAME}")
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
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/llipinsk82-rgb/client-ssh/releases/latest"
    }
}
