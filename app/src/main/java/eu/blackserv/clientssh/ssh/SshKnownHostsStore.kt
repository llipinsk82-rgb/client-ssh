package eu.blackserv.clientssh.ssh

import android.content.Context
import java.io.File

object SshKnownHostsStore {
    private const val ACTIVE_FILE_NAME = "known_hosts"
    private const val LEGACY_BACKUP_FILE_NAME = "known_hosts.accept-new-unverified"
    private const val MIGRATION_MARKER_FILE_NAME = ".explicit-host-key-verification-v1"
    private val lock = Any()

    fun prepare(context: Context): File = prepareDirectory(File(context.filesDir, "ssh"))

    internal fun prepareDirectory(directory: File): File = synchronized(lock) {
        check(directory.exists() || directory.mkdirs()) {
            "Nie można utworzyć prywatnego katalogu known_hosts"
        }

        val active = File(directory, ACTIVE_FILE_NAME)
        val marker = File(directory, MIGRATION_MARKER_FILE_NAME)

        if (!marker.isFile) {
            if (active.isFile && active.length() > 0L) {
                val backup = nextBackupFile(directory)
                active.copyTo(backup, overwrite = false)
                securePrivateFile(backup)
            }
            active.outputStream().use { output -> output.flush() }
            securePrivateFile(active)

            val temporaryMarker = File(directory, "$MIGRATION_MARKER_FILE_NAME.tmp")
            temporaryMarker.writeText("v1\n")
            securePrivateFile(temporaryMarker)
            check(temporaryMarker.renameTo(marker) || runCatching {
                temporaryMarker.copyTo(marker, overwrite = true)
                temporaryMarker.delete()
                true
            }.getOrDefault(false)) {
                "Nie można zatwierdzić migracji known_hosts"
            }
            securePrivateFile(marker)
        } else if (!active.exists()) {
            check(active.createNewFile()) { "Nie można utworzyć aktywnego known_hosts" }
            securePrivateFile(active)
        }

        active
    }

    private fun nextBackupFile(directory: File): File {
        val first = File(directory, LEGACY_BACKUP_FILE_NAME)
        if (!first.exists()) return first
        for (index in 2..100) {
            val candidate = File(directory, "$LEGACY_BACKUP_FILE_NAME.$index")
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
