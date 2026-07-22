package eu.blackserv.clientssh.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import eu.blackserv.clientssh.model.AuthenticationMethod
import eu.blackserv.clientssh.model.HostProfile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Vector

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
    private var session: Session? = null
    private var channel: ChannelSftp? = null

    fun connect(profile: HostProfile): String {
        disconnect()

        val host = profile.host.trim()
        val username = profile.username.trim()
        require(host.isNotBlank()) { "Host jest pusty." }
        require(username.isNotBlank()) { "Użytkownik SSH jest pusty." }

        knownHostsFile.parentFile?.mkdirs()
        if (!knownHostsFile.exists()) knownHostsFile.createNewFile()

        val jsch = JSch().apply {
            setKnownHosts(knownHostsFile.absolutePath)
            if (profile.authenticationMethod == AuthenticationMethod.PRIVATE_KEY) {
                val passphrase = profile.privateKeyPassphrase
                    .takeIf(String::isNotEmpty)
                    ?.toByteArray(StandardCharsets.UTF_8)
                addIdentity(
                    profile.id,
                    profile.privateKey.toByteArray(StandardCharsets.UTF_8),
                    null,
                    passphrase,
                )
            }
        }

        val newSession = jsch.getSession(username, host, profile.port).apply {
            if (profile.authenticationMethod == AuthenticationMethod.PASSWORD) {
                setPassword(profile.password)
            }
            setConfig("StrictHostKeyChecking", "accept-new")
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

        newSession.connect(CONNECT_TIMEOUT_MS)
        val newChannel = newSession.openChannel("sftp") as ChannelSftp
        newChannel.connect(CHANNEL_TIMEOUT_MS)

        session = newSession
        channel = newChannel
        return newChannel.pwd()
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
        channel = null
        session = null
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

    private fun Throwable.readableMessage(host: String): String {
        val raw = message?.trim().orEmpty()
        return when {
            raw.contains("Auth fail", ignoreCase = true) -> "Nieprawidłowy login, hasło lub klucz SSH."
            raw.contains("reject HostKey", ignoreCase = true) -> "Klucz hosta SSH zmienił się. Połączenie zostało zablokowane."
            raw.contains("UnknownHostException", ignoreCase = true) ||
                raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Nie można znaleźć hosta: $host. Sprawdź internet, DNS albo literówkę w profilu."
            raw.contains("Permission denied", ignoreCase = true) -> "Brak uprawnień do tej operacji."
            raw.contains("timeout", ignoreCase = true) -> "Przekroczono czas oczekiwania na połączenie."
            raw.isNotEmpty() -> raw
            else -> javaClass.simpleName
        }
    }

    fun readableError(error: Throwable, profile: HostProfile): String = error.readableMessage(profile.host.trim())

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val CHANNEL_TIMEOUT_MS = 10_000
        private val DATE_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        }
    }
}
