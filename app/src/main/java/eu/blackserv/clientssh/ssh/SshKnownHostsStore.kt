package eu.blackserv.clientssh.ssh

import android.content.Context
import com.jcraft.jsch.JSch
import java.io.File

object SshKnownHostsStore {
    private const val ACTIVE_FILE_NAME = "known_hosts"
    private const val LEGACY_BACKUP_FILE_NAME = "known_hosts.accept-new-unverified"
    private const val MIGRATION_MARKER_FILE_NAME = ".explicit-host-key-verification-v1"
    private const val PORT_SCOPE_BACKUP_FILE_NAME = "known_hosts.pre-port-scope-v2"
    private const val PORT_SCOPE_MARKER_FILE_NAME = ".host-key-port-scope-v2"
    private val lock = Any()

    fun prepare(context: Context): File = prepareDirectory(File(context.filesDir, "ssh"))

    fun forget(context: Context, host: String, port: Int): Boolean =
        forgetDirectory(File(context.filesDir, "ssh"), host, port)

    internal fun forgetDirectory(directory: File, host: String, port: Int): Boolean = synchronized(lock) {
        val active = prepareDirectory(directory)
        val alias = sshHostKeyAlias(host, port)
        val jsch = JSch().apply { setKnownHosts(active.absolutePath) }
        val repository = jsch.hostKeyRepository
        val before = repository.getHostKey(alias, null)
        if (before.isNullOrEmpty()) {
            true
        } else {
            repository.remove(alias, null)
            repository.getHostKey(alias, null).isNullOrEmpty()
        }
    }

    internal fun prepareDirectory(directory: File): File = synchronized(lock) {
        check(directory.exists() || directory.mkdirs()) {
            "Nie można utworzyć prywatnego katalogu known_hosts"
        }
        check(directory.isDirectory) { "Ścieżka SSH nie jest katalogiem" }

        val active = File(directory, ACTIVE_FILE_NAME)
        val marker = File(directory, MIGRATION_MARKER_FILE_NAME)
        val portScopeMarker = File(directory, PORT_SCOPE_MARKER_FILE_NAME)
        check(!active.exists() || active.isFile) { "Ścieżka known_hosts nie jest plikiem" }
        check(!marker.exists() || marker.isFile) { "Marker migracji known_hosts nie jest plikiem" }
        check(!portScopeMarker.exists() || portScopeMarker.isFile) {
            "Marker migracji host:port nie jest plikiem"
        }

        if (!marker.isFile) {
            archiveIfNotEmpty(directory, active, LEGACY_BACKUP_FILE_NAME)
            truncateAndSecure(active)
            writeMarker(directory, marker, "v1
")
        } else if (!active.exists()) {
            check(active.createNewFile()) { "Nie można utworzyć aktywnego known_hosts" }
            securePrivateFile(active)
        }

        if (!portScopeMarker.isFile) {
            archiveIfNotEmpty(directory, active, PORT_SCOPE_BACKUP_FILE_NAME)
            truncateAndSecure(active)
            writeMarker(directory, portScopeMarker, "v2
")
        }

        check(active.isFile) { "Aktywny known_hosts nie jest plikiem" }
        active
    }

    private fun archiveIfNotEmpty(directory: File, active: File, baseName: String) {
        if (active.isFile && active.length() > 0L) {
            val backup = nextBackupFile(directory, baseName)
            active.copyTo(backup, overwrite = false)
            securePrivateFile(backup)
        }
    }

    private fun truncateAndSecure(active: File) {
        active.outputStream().use { output -> output.flush() }
        securePrivateFile(active)
    }

    private fun writeMarker(directory: File, marker: File, value: String) {
        val temporaryMarker = File(directory, "${marker.name}.tmp")
        check(!temporaryMarker.exists() || temporaryMarker.isFile) {
            "Tymczasowy marker migracji known_hosts nie jest plikiem"
        }
        temporaryMarker.writeText(value)
        securePrivateFile(temporaryMarker)
        check(temporaryMarker.renameTo(marker) || runCatching {
            temporaryMarker.copyTo(marker, overwrite = true)
            temporaryMarker.delete()
            true
        }.getOrDefault(false)) {
            "Nie można zatwierdzić migracji known_hosts"
        }
        securePrivateFile(marker)
    }

    private fun nextBackupFile(directory: File, baseName: String): File {
        val first = File(directory, baseName)
        if (!first.exists()) return first
        for (index in 2..100) {
            val candidate = File(directory, "$baseName.$index")
            if (!candidate.exists()) return candidate
        }
        error("Zbyt wiele kopii migracyjnych known_hosts")
    }

    private fun securePrivateFile(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        check(file.setReadable(true, true)) { "Nie można ustawić prywatnego odczytu pliku" }
        check(file.setWritable(true, true)) { "Nie można ustawić prywatnego zapisu pliku" }
    }
}
