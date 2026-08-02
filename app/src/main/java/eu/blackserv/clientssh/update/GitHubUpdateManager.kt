package eu.blackserv.clientssh.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
    val apkSha256: String,
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
                partial.delete()
                target.delete()

                try {
                    downloadToFile(release.apkUrl, partial)
                    verifySha256(partial, release.apkSha256)
                    check(partial.renameTo(target)) {
                        "Nie udało się przygotować pliku APK do instalacji."
                    }
                    verifyInstallableApk(target)
                    target
                } catch (error: Throwable) {
                    partial.delete()
                    target.delete()
                    throw error
                }
            }
            post { onResult(result) }
        }
    }

    fun install(apk: File): InstallLaunchResult = runCatching {
        if (!apk.exists() || apk.length() <= 0L) {
            return InstallLaunchResult.Error("Plik APK nie istnieje albo jest pusty. Pobierz aktualizację ponownie.")
        }

        verifyInstallableApk(apk)

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
        val connection = openConnection(LATEST_RELEASE_URL, api = true)
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val release = JSONObject(response)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null

        val tag = release.optString("tag_name")
        val version = tag.removePrefix("v").removePrefix("V")
        if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return null

        val assets = release.optJSONArray("assets") ?: error("Wydanie $tag nie zawiera plików aktualizacji.")
        val apkAsset = findReleaseApkAsset(assets)
            ?: error("Wydanie $tag nie zawiera podpisanego APK release.")
        val apkName = apkAsset.getString("name")
        val shaAsset = findShaAsset(assets, apkName)
            ?: error("Wydanie $tag nie zawiera wymaganej sumy SHA-256 dla APK.")
        val checksum = fetchSha256(shaAsset.getString("browser_download_url"))
            ?: error("Nie udało się odczytać sumy SHA-256 wydania $tag.")

        return ReleaseInfo(
            tag = tag,
            version = version,
            notes = release.optString("body").ifBlank { "Brak opisu zmian." },
            apkName = apkName,
            apkUrl = apkAsset.getString("browser_download_url"),
            apkSha256 = checksum,
            releaseUrl = release.optString("html_url"),
        )
    }

    private fun findReleaseApkAsset(assets: JSONArray): JSONObject? = (0 until assets.length())
        .asSequence()
        .map { assets.getJSONObject(it) }
        .filter { asset ->
            val name = asset.optString("name")
            name.endsWith(".apk", ignoreCase = true) &&
                !name.contains("debug", ignoreCase = true) &&
                !name.contains("unsigned", ignoreCase = true)
        }
        .sortedBy { it.optString("name") }
        .firstOrNull()

    private fun findShaAsset(assets: JSONArray, apkName: String): JSONObject? = (0 until assets.length())
        .asSequence()
        .map { assets.getJSONObject(it) }
        .firstOrNull { it.optString("name").equals("$apkName.sha256", ignoreCase = true) }

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

    private fun verifyInstallableApk(apk: File) {
        val candidate = packageInfoForArchive(apk)
            ?: error("Android nie rozpoznaje pobranego pliku jako prawidłowego APK.")
        require(candidate.packageName == context.packageName) {
            "Pobrany APK należy do innej aplikacji (${candidate.packageName})."
        }

        val installed = installedPackageInfo()
        val installedCode = versionCode(installed)
        val candidateCode = versionCode(candidate)
        require(candidateCode > installedCode) {
            "Pobrany APK nie jest nowszy: versionCode $candidateCode, zainstalowany $installedCode."
        }

        val installedCertificates = signingCertificateDigests(installed)
        val candidateCertificates = signingCertificateDigests(candidate)
        require(installedCertificates.isNotEmpty() && candidateCertificates.isNotEmpty()) {
            "Nie udało się zweryfikować certyfikatu podpisu APK."
        }
        require(candidateCertificates.any { it in installedCertificates }) {
            "Aktualizacja jest podpisana innym kluczem niż zainstalowana aplikacja. " +
                "Nie instaluj jej jako OTA; potrzebna jest oficjalna paczka podpisana stałym kluczem Client SSH."
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfoForArchive(apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signingCertificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            info.signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature -> sha256(signature.toByteArray()) }
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
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

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

    private fun post(block: () -> Unit) {
        android.os.Handler(context.mainLooper).post(block)
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/llipinsk82-rgb/client-ssh/releases/latest"
        private val SHA256_REGEX = Regex("[a-fA-F0-9]{64}")
    }
}

internal fun isNewerVersion(remote: String, local: String): Boolean {
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
