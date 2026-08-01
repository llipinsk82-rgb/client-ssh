package eu.blackserv.clientssh.terminal

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TerminalSessionLogStoreTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("terminal-log-test-").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `full log keeps output hidden from the bounded screen by clear`() {
        val store = newStore(File(root, "logs"))
        val file = store.open("Franek VPS", "example.test", 3399)
        store.appendInbound("before\n\u001B[2J\u001B[H\u001B[32mafter\u001B[0m\n")
        store.close()

        val text = file.readText()
        assertTrue(text.contains("before"))
        assertTrue(text.contains("after"))
        assertFalse(text.contains('\u001B'))
    }

    @Test
    fun `session keeper resumes the same private file`() {
        val directory = File(root, "logs")
        val first = newStore(directory)
        val file = first.open("server", "host", 22)
        first.appendInbound("one\n")
        first.close()

        val second = newStore(directory)
        val resumed = second.open("server", "host", 22, file.absolutePath)
        second.appendInbound("two\n")
        second.close()

        assertEquals(file.canonicalFile, resumed.canonicalFile)
        assertTrue(file.readText().contains("one"))
        assertTrue(file.readText().contains("two"))
        assertTrue(file.readText().contains("wznowił"))
    }

    @Test
    fun `export resolver rejects files outside the private log directory`() {
        val directory = File(root, "logs").apply { mkdirs() }
        val inside = File(directory, "inside.log").apply { writeText("ok") }
        val outside = File(root, "outside.log").apply { writeText("bad") }

        assertEquals(inside.canonicalFile, TerminalSessionLogStore.resolveForExport(directory, inside.absolutePath))
        assertNull(TerminalSessionLogStore.resolveForExport(directory, outside.absolutePath))
        assertNull(TerminalSessionLogStore.resolveForExport(directory, null))
    }

    @Test
    fun `safe limit preserves valid utf8 and writes a truncation marker`() {
        val store = TerminalSessionLogStore(File(root, "logs"), fixedClock(), maxBytes = 4096)
        val file = store.open("server", "host", 22)
        repeat(100) { store.appendInbound("0123456789".repeat(100) + "ą\n") }
        store.close()

        assertTrue(file.length() <= 4096)
        assertTrue(file.readText().contains("bezpieczny limit"))
    }


    @Test
    fun `retention keeps at most configured count including active file`() {
        val directory = File(root, "logs").apply { mkdirs() }
        repeat(4) { index ->
            File(directory, "old-$index.log").apply {
                writeText("old")
                setLastModified(1_000L + index)
            }
        }

        val store = TerminalSessionLogStore(
            directory = directory,
            clock = fixedClock(),
            maxBytes = 32 * 1024,
            maxRetainedFiles = 3,
            retentionMillis = 0,
        )
        val active = store.open("server", "host", 22)
        store.close()

        val files = directory.listFiles().orEmpty().filter { it.extension == "log" }
        assertEquals(3, files.size)
        assertTrue(active in files)
    }

    @Test
    fun `export resolver rejects a symlink escaping private directory`() {
        val directory = File(root, "logs").apply { mkdirs() }
        val outside = File(root, "outside.log").apply { writeText("bad") }
        val link = File(directory, "escape.log")
        runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.getOrElse { return }

        assertNull(TerminalSessionLogStore.resolveForExport(directory, link.absolutePath))
    }

    @Test
    fun `ansi sanitizer handles escape sequences split between ssh chunks`() {
        val sanitizer = TerminalLogSanitizer()
        assertEquals("A", sanitizer.sanitize("A\u001B["))
        assertEquals("B", sanitizer.sanitize("31mB\u001B]"))
        assertEquals("", sanitizer.sanitize("0;title"))
        assertEquals("C", sanitizer.sanitize("\u0007C"))
    }

    private fun newStore(directory: File) = TerminalSessionLogStore(
        directory = directory,
        clock = fixedClock(),
        maxBytes = 32 * 1024,
    )

    private fun fixedClock(): Clock = Clock.fixed(
        Instant.parse("2026-08-01T15:00:00Z"),
        ZoneOffset.UTC,
    )
}
