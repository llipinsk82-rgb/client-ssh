package eu.blackserv.clientssh.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.ssh.PortScopedHostKeyRepository
import eu.blackserv.clientssh.ssh.applyProfileSshCompatibility
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Vector

internal const val SFTP_STRICT_HOST_KEY_CHECKING = "yes"

data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val size: Long,
    val permissions: String,
    val modified: String,
)

class SftpClient(private val knownHostsFile: File) {
    private var jsch: JSch? = null
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    fun connect(profile: HostProfile): String {
        disconnect()

        val host = profile.host.trim()
        val username = profile.username.trim()
        require(host.isNotBlank()) { "Host jest pusty." }
        require(username.isNotBlank()) { "Użytkownik SSH jest pusty." }
        check(knownHostsFile.isFile) { "Magazyn known_hosts nie jest gotowy." }

        val newJsch = JSch().apply {
            setKnownHosts(knownHostsFile.absolutePath)
        }

        if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY) {
            require(profile.privateKey.isNotBlank()) { "Klucz prywatny jest pusty." }
            val privateKeyBytes = profile.privateKey.toByteArray(StandardCharsets.UTF_8)
            val passphraseBytes = profile.privateKeyPassphrase
                .takeIf(String::isNotEmpty)
                ?.toByteArray(StandardCharsets.UTF_8)
            try {
                newJsch.addIdentity(profile.id, privateKeyBytes, null, passphraseBytes)
            } finally {
                privateKeyBytes.fill(0)
                passphraseBytes?.fill(0)
            }
        }

        val newSession = newJsch.getSession(username, host, profile.port).apply {
            hostKeyRepository = PortScopedHostKeyRepository(
                delegate = newJsch.hostKeyRepository,
                displayHost = host,
                port = profile.port,
            )
            if (profile.authenticationMethod == AuthenticationMethod.PASSWORD) {
                setPassword(profile.password)
            }
            applyProfileSshCompatibility(profile)
            setConfig("StrictHostKeyChecking", SFTP_STRICT_HOST_KEY_CHECKING)
            setConfig(
                "PreferredAuthentications",
                when (profile.authenticationMethod) {
                    AuthenticationMethod.PASSWORD -> "password,keyboard-interactive"
                    AuthenticationMethod.PRIVATE_KEY -> "publickey"
                    AuthenticationMethod.INTERACTIVE -> "keyboard-interactive,password"
                },
            )
            setServerAliveInterval(15_000)
            setServerAliveCountMax(3)
        }

        var newChannel: ChannelSftp? = null
        try {
            newSession.connect(CONNECT_TIMEOUT_MS)
            newChannel = newSession.openChannel("sftp") as ChannelSftp
            newChannel.connect(CHANNEL_TIMEOUT_MS)

            jsch = newJsch
            session = newSession
            channel = newChannel
            return newChannel.pwd()
        } catch (error: Throwable) {
            runCatching { newChannel?.disconnect() }
            runCatching { newSession.disconnect() }
            runCatching { newJsch.removeAllIdentity() }
            throw error
        }
    }

    fun listCurrent(): List<SftpEntry> {
        val sftp = requireChannel()
        return entriesFrom(sftp.ls(".") as Vector<*>, sftp.pwd())
    }

    fun openDirectory(path: String): Pair<String, List<SftpEntry>> {
        val sftp = requireChannel()
        sftp.cd(path)
        return sftp.pwd() to listCurrent()
    }

    fun parentDirectory(): Pair<String, List<SftpEntry>> = openDirectory("..")

    fun mkdir(name: String): Pair<String, List<SftpEntry>> {
        require(name.isNotBlank()) { "Nazwa katalogu jest pusta." }
        requireChannel().mkdir(name.trim())
        return currentPath() to listCurrent()
    }

    fun rename(from: String, toName: String): Pair<String, List<SftpEntry>> {
        require(toName.isNotBlank()) { "Nowa nazwa jest pusta." }
        requireChannel().rename(from, joinPath(currentPath(), toName.trim()))
        return currentPath() to listCurrent()
    }

    fun delete(entry: SftpEntry): Pair<String, List<SftpEntry>> {
        val sftp = requireChannel()
        if (entry.isDirectory) sftp.rmdir(entry.path) else sftp.rm(entry.path)
        return currentPath() to listCurrent()
    }

    fun download(remotePath: String, output: OutputStream) {
        requireChannel().get(remotePath, output)
    }

    fun upload(input: InputStream, fileName: String): Pair<String, List<SftpEntry>> {
        require(fileName.isNotBlank()) { "Nazwa pliku jest pusta." }
        requireChannel().put(input, joinPath(currentPath(), fileName.trim()))
        return currentPath() to listCurrent()
    }

    fun currentPath(): String = requireChannel().pwd()

    fun disconnect() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        runCatching { jsch?.removeAllIdentity() }
        channel = null
        session = null
        jsch = null
    }

    private fun requireChannel(): ChannelSftp = channel ?: error("SFTP nie jest połączone.")

    private fun entriesFrom(rawEntries: Vector<*>, currentPath: String): List<SftpEntry> = rawEntries
        .asSequence()
        .mapNotNull { it as? ChannelSftp.LsEntry }
        .filterNot { it.filename == "." || it.filename == ".." }
        .map { entry ->
            val attrs = entry.attrs
            SftpEntry(
                name = entry.filename,
                path = joinPath(currentPath, entry.filename),
                isDirectory = attrs.isDir,
                isFile = !attrs.isDir,
                size = attrs.size,
                permissions = runCatching { attrs.permissionsString }.getOrDefault(""),
                modified = DATE_FORMAT.get().format(Date(attrs.mTime.toLong() * 1000L)),
            )
        }
        .sortedWith(compareByDescending<SftpEntry> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
        .toList()

    private fun joinPath(base: String, name: String): String = when {
        name.startsWith("/") -> name
        base == "/" -> "/$name"
        else -> "$base/$name"
    }

    fun readableError(error: Throwable, profile: HostProfile): String =
        safeSftpErrorMessage(error, profile)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val CHANNEL_TIMEOUT_MS = 10_000
        private val DATE_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        }
    }
}

internal fun safeSftpErrorMessage(error: Throwable, profile: HostProfile): String {
    val raw = error.message?.trim().orEmpty()
    val algorithmNegotiationFailed =
        error.javaClass.simpleName.contains("AlgoNego", ignoreCase = true) ||
            raw.contains("algorithm negotiation", ignoreCase = true)
    return when {
        algorithmNegotiationFailed ->
            "Nie udało się uzgodnić algorytmów SFTP z tym serwerem. Automatyczna zgodność nie znalazła wspólnego zestawu."

        raw.contains("host key has changed", ignoreCase = true) ||
            raw.contains("hostkey has been changed", ignoreCase = true) ->
            "Klucz hosta SSH zmienił się. Połączenie SFTP zostało zablokowane."

        raw.contains("reject HostKey", ignoreCase = true) ||
            raw.contains("unknownhostkey", ignoreCase = true) ->
            "Klucz hosta SSH nie jest zweryfikowany. Najpierw połącz się przez Terminal i porównaj fingerprint."

        raw.contains("Auth fail", ignoreCase = true) ->
            "Nieprawidłowy login, hasło lub klucz SSH."

        raw.contains("UnknownHostException", ignoreCase = true) ||
            raw.contains("Unable to resolve host", ignoreCase = true) ->
            "Nie można znaleźć hosta: ${profile.host.trim()}. Sprawdź internet, DNS albo literówkę w profilu."

        raw.contains("Permission denied", ignoreCase = true) ->
            "Brak uprawnień do tej operacji SFTP."

        raw.contains("timeout", ignoreCase = true) ->
            "Przekroczono czas oczekiwania na połączenie SFTP."

        raw.contains("klucz prywatny jest pusty", ignoreCase = true) ->
            "Klucz prywatny jest pusty. Edytuj profil i wklej poprawny klucz."

        else -> "Nie udało się wykonać operacji SFTP."
    }
}
