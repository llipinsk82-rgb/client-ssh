package eu.blackserv.clientssh.terminal

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Streams inbound terminal output to an app-private file independently from the bounded UI buffer.
 * Outbound user input is deliberately not accepted by this API so hidden password/passphrase entry
 * cannot be copied into the transcript by the app.
 */
internal class TerminalSessionLogStore(
    private val directory: File,
    private val clock: Clock = Clock.systemUTC(),
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxRetainedFiles: Int = DEFAULT_MAX_RETAINED_FILES,
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
) : Closeable {
    private val sanitizer = TerminalLogSanitizer()
    private var output: BufferedOutputStream? = null
    private var activeFile: File? = null
    private var bytesWritten: Long = 0
    private var truncated = false

    init {
        require(maxBytes >= MINIMUM_MAX_BYTES) { "maxBytes is too small" }
        require(maxRetainedFiles >= 1) { "maxRetainedFiles must be positive" }
        require(retentionMillis >= 0) { "retentionMillis cannot be negative" }
    }

    @Synchronized
    fun open(
        profileName: String,
        host: String,
        port: Int,
        resumePath: String? = null,
    ): File {
        close()
        require(directory.exists() || directory.mkdirs()) { "Cannot create private terminal log directory" }
        require(directory.isDirectory) { "Terminal log path is not a directory" }

        val resumeFile = resolveForExport(directory, resumePath)
        val file = resumeFile ?: File(directory, buildFilename(profileName))
        val existed = file.isFile && file.length() > 0L
        output = BufferedOutputStream(FileOutputStream(file, true), OUTPUT_BUFFER_BYTES)
        activeFile = file
        bytesWritten = file.length()
        truncated = bytesWritten >= payloadLimitBytes()
        sanitizer.reset()
        prune(excluded = file)

        if (!truncated) {
            if (existed) {
                appendNotice("Session Keeper wznowił zapis do istniejącego logu.")
            } else {
                appendNotice("Początek pełnego logu sesji")
                appendPlain("Profil: ${singleLine(profileName).ifBlank { "Profil" }}\n")
                appendPlain("Host: ${singleLine(host)}:$port\n")
                appendPlain("Czas UTC: ${ISO_INSTANT.format(clock.instant())}\n")
                appendPlain(
                    "Zakres: wyłącznie dane odebrane z terminala; lokalne niewyświetlane wpisy nie są dopisywane.\n\n",
                )
            }
        }
        return file
    }

    @Synchronized
    fun appendInbound(text: String) {
        if (text.isEmpty() || output == null || truncated) return
        appendPlain(sanitizer.sanitize(text))
    }

    @Synchronized
    fun appendNotice(message: String) {
        if (output == null || truncated) return
        appendPlain("\n[Client SSH] ${singleLine(message)}\n")
    }

    @Synchronized
    fun currentFile(): File? = activeFile

    @Synchronized
    override fun close() {
        runCatching { output?.flush() }
        runCatching { output?.close() }
        output = null
        activeFile = null
        bytesWritten = 0
        truncated = false
        sanitizer.reset()
    }

    private fun appendPlain(text: String) {
        if (text.isEmpty() || truncated) return
        val sink = output ?: return
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val marker = TRUNCATION_MARKER.toByteArray(StandardCharsets.UTF_8)
        val remainingPayload = payloadLimitBytes() - bytesWritten

        if (bytes.size.toLong() <= remainingPayload) {
            sink.write(bytes)
            sink.flush()
            bytesWritten += bytes.size
            return
        }

        val cut = validUtf8PrefixLength(
            bytes,
            remainingPayload.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        if (cut > 0) {
            sink.write(bytes, 0, cut)
            bytesWritten += cut
        }
        sink.write(marker)
        bytesWritten += marker.size
        sink.flush()
        truncated = true
    }

    private fun payloadLimitBytes(): Long =
        maxBytes - TRUNCATION_MARKER.toByteArray(StandardCharsets.UTF_8).size

    private fun prune(excluded: File?) {
        val now = clock.millis()
        val excludedCanonical = excluded?.runCatching { canonicalFile }?.getOrNull()
        val files = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("log", ignoreCase = true) }
            .sortedByDescending(File::lastModified)

        var retained = 0
        files.forEach { file ->
            val canonical = runCatching { file.canonicalFile }.getOrNull()
            val isExcluded = canonical == excludedCanonical
            val expired = retentionMillis > 0 && now - file.lastModified() > retentionMillis
            val overCount = retained >= maxRetainedFiles
            if (!isExcluded && (expired || overCount)) {
                runCatching { file.delete() }
            } else {
                retained++
            }
        }
    }

    private fun singleLine(value: String): String = value.replace('\r', ' ').replace('\n', ' ').trim()

    private fun buildFilename(profileName: String): String {
        val safeName = singleLine(profileName)
            .ifBlank { "session" }
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-')
            .take(MAX_PROFILE_FILENAME_CHARS)
            .ifBlank { "session" }
            .lowercase(Locale.ROOT)
        val timestamp = FILE_TIMESTAMP.format(clock.instant())
        val nonce = UUID.randomUUID().toString().take(8)
        return "$safeName-$timestamp-$nonce.log"
    }

    companion object {
        const val DIRECTORY_NAME = "terminal-session-logs"
        private const val OUTPUT_BUFFER_BYTES = 16 * 1024
        private const val MAX_PROFILE_FILENAME_CHARS = 40
        private const val MINIMUM_MAX_BYTES = 4 * 1024L
        private const val DEFAULT_MAX_BYTES = 128L * 1024L * 1024L
        private const val DEFAULT_MAX_RETAINED_FILES = 10
        private const val DEFAULT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        private const val TRUNCATION_MARKER =
            "\n[Client SSH] Log osiągnął bezpieczny limit rozmiaru. Dalszy zapis został zatrzymany.\n"
        private val ISO_INSTANT = DateTimeFormatter.ISO_INSTANT
        private val FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC)

        /** Resolves only regular .log files located inside the app-private log directory. */
        fun resolveForExport(directory: File, candidatePath: String?): File? {
            if (candidatePath.isNullOrBlank()) return null
            val root = runCatching { directory.canonicalFile }.getOrNull() ?: return null
            val candidate = runCatching { File(candidatePath).canonicalFile }.getOrNull() ?: return null
            val rootPrefix = root.path + File.separator
            return candidate.takeIf {
                it.isFile &&
                    it.extension.equals("log", ignoreCase = true) &&
                    it.path.startsWith(rootPrefix)
            }
        }

        private fun validUtf8PrefixLength(bytes: ByteArray, maxLength: Int): Int {
            var cut = maxLength.coerceIn(0, bytes.size)
            while (cut > 0 && cut < bytes.size && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
            return cut
        }
    }
}

/** Stateful ANSI/OSC stripper that also handles escape sequences split between SSH chunks. */
internal class TerminalLogSanitizer {
    private enum class State { NORMAL, ESCAPE, CSI, STRING, STRING_ESCAPE, CHARSET }

    private var state = State.NORMAL

    fun reset() {
        state = State.NORMAL
    }

    fun sanitize(chunk: String): String {
        if (chunk.isEmpty()) return ""
        val output = StringBuilder(chunk.length)
        chunk.forEach { character ->
            when (state) {
                State.NORMAL -> when (character) {
                    ESC -> state = State.ESCAPE
                    NUL -> Unit
                    else -> output.append(character)
                }

                State.ESCAPE -> when (character) {
                    '[' -> state = State.CSI
                    ']', 'P', '^', '_' -> state = State.STRING
                    '(', ')', '*', '+', '-', '.', '/' -> state = State.CHARSET
                    ESC -> state = State.ESCAPE
                    else -> state = State.NORMAL
                }

                State.CSI -> if (character in '@'..'~') state = State.NORMAL
                State.STRING -> when (character) {
                    BEL -> state = State.NORMAL
                    ESC -> state = State.STRING_ESCAPE
                }

                State.STRING_ESCAPE -> when (character) {
                    '\\' -> state = State.NORMAL
                    ESC -> state = State.STRING_ESCAPE
                    else -> state = State.STRING
                }

                State.CHARSET -> state = State.NORMAL
            }
        }
        return output.toString()
    }

    private companion object {
        const val ESC = '\u001B'
        const val BEL = '\u0007'
        const val NUL = '\u0000'
    }
}
