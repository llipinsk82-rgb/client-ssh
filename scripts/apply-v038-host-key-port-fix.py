#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Expected source fragment not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


root = Path(__file__).resolve().parents[1]

# 1. Canonical endpoint identity: every SSH server is host + port.
host_trust = root / "app/src/main/java/eu/blackserv/clientssh/ssh/HostKeyTrust.kt"
replace_once(
    host_trust,
    "enum class HostKeyTrustKind {\n    UNKNOWN,\n    CHANGED,\n}\n",
    "enum class HostKeyTrustKind {\n    UNKNOWN,\n    CHANGED,\n}\n\n"
    "internal fun sshHostKeyAlias(host: String, port: Int): String {\n"
    "    require(port in 1..65_535) { \"Port SSH poza zakresem\" }\n"
    "    val trimmed = host.trim()\n"
    "    require(trimmed.isNotBlank()) { \"Host SSH jest pusty\" }\n"
    "    val normalized = if (trimmed.startsWith(\"[\") && trimmed.endsWith(\"]\") && trimmed.length > 2) {\n"
    "        trimmed.substring(1, trimmed.length - 1)\n"
    "    } else {\n"
    "        trimmed\n"
    "    }\n"
    "    return \"[$normalized]:$port\"\n"
    "}\n",
)
replace_once(
    host_trust,
    "class InteractiveHostKeyRepository(\n",
    "class PortScopedHostKeyRepository(\n"
    "    private val delegate: HostKeyRepository,\n"
    "    private val displayHost: String,\n"
    "    private val port: Int,\n"
    ") : HostKeyRepository {\n"
    "    private val endpointAlias: String\n"
    "        get() = sshHostKeyAlias(displayHost, port)\n\n"
    "    override fun check(repositoryHost: String, key: ByteArray): Int =\n"
    "        delegate.check(endpointAlias, key)\n\n"
    "    override fun add(hostkey: HostKey, userinfo: UserInfo?) = delegate.add(hostkey, userinfo)\n\n"
    "    override fun remove(host: String, type: String?) = delegate.remove(endpointAlias, type)\n\n"
    "    override fun remove(host: String, type: String?, key: ByteArray?) =\n"
    "        delegate.remove(endpointAlias, type, key)\n\n"
    "    override fun getKnownHostsRepositoryID(): String = delegate.knownHostsRepositoryID\n\n"
    "    override fun getHostKey(): Array<HostKey> = delegate.hostKey\n\n"
    "    override fun getHostKey(host: String?, type: String?): Array<HostKey> =\n"
    "        delegate.getHostKey(if (host == null) null else endpointAlias, type)\n"
    "}\n\n"
    "class InteractiveHostKeyRepository(\n",
)
replace_once(
    host_trust,
    "        val existing = delegate.check(repositoryHost, key)\n",
    "        val endpointHost = sshHostKeyAlias(displayHost, port)\n"
    "        val existing = delegate.check(endpointHost, key)\n",
)
replace_once(
    host_trust,
    "        val candidate = PendingKey(request.id, repositoryHost, key.copyOf())\n",
    "        val candidate = PendingKey(request.id, endpointHost, key.copyOf())\n",
)

# 2. SFTP and background telemetry use exactly the same host:port identity.
sftp = root / "app/src/main/java/eu/blackserv/clientssh/sftp/SftpClient.kt"
replace_once(
    sftp,
    "import eu.blackserv.clientssh.model.HostProfile\n",
    "import eu.blackserv.clientssh.model.HostProfile\n"
    "import eu.blackserv.clientssh.ssh.PortScopedHostKeyRepository\n",
)
replace_once(
    sftp,
    "        val newSession = newJsch.getSession(username, host, profile.port).apply {\n",
    "        val newSession = newJsch.getSession(username, host, profile.port).apply {\n"
    "            hostKeyRepository = PortScopedHostKeyRepository(\n"
    "                delegate = newJsch.hostKeyRepository,\n"
    "                displayHost = host,\n"
    "                port = profile.port,\n"
    "            )\n",
)

telemetry = root / "app/src/main/java/eu/blackserv/clientssh/health/JschSshTelemetryTransport.kt"
replace_once(
    telemetry,
    "import eu.blackserv.clientssh.model.HostProfile\n",
    "import eu.blackserv.clientssh.model.HostProfile\n"
    "import eu.blackserv.clientssh.ssh.PortScopedHostKeyRepository\n",
)
replace_once(
    telemetry,
    "            ).apply {\n                if (profile.authenticationMethod == AuthenticationMethod.PASSWORD) {\n",
    "            ).apply {\n"
    "                hostKeyRepository = PortScopedHostKeyRepository(\n"
    "                    delegate = jsch.hostKeyRepository,\n"
    "                    displayHost = profile.host.trim(),\n"
    "                    port = profile.port,\n"
    "                )\n"
    "                if (profile.authenticationMethod == AuthenticationMethod.PASSWORD) {\n",
)

# 3. One-time safe migration and exact endpoint reset.
known_hosts_store = root / "app/src/main/java/eu/blackserv/clientssh/ssh/SshKnownHostsStore.kt"
known_hosts_store.write_text('''package eu.blackserv.clientssh.ssh

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
            writeMarker(directory, marker, "v1\n")
        } else if (!active.exists()) {
            check(active.createNewFile()) { "Nie można utworzyć aktywnego known_hosts" }
            securePrivateFile(active)
        }

        if (!portScopeMarker.isFile) {
            archiveIfNotEmpty(directory, active, PORT_SCOPE_BACKUP_FILE_NAME)
            truncateAndSecure(active)
            writeMarker(directory, portScopeMarker, "v2\n")
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
''')

# 4. Changed-key dialog offers a safe, endpoint-specific reset, never a blind bypass.
dialog = root / "app/src/main/java/eu/blackserv/clientssh/ui/screens/HostKeyTrustDialog.kt"
replace_once(
    dialog,
    "import androidx.compose.material.icons.filled.ContentCopy\n",
    "import androidx.compose.material.icons.filled.ContentCopy\n"
    "import androidx.compose.material.icons.filled.DeleteForever\n",
)
replace_once(
    dialog,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\n"
    "import androidx.compose.runtime.remember\n"
    "import androidx.compose.runtime.mutableStateOf\n",
)
replace_once(
    dialog,
    "import androidx.compose.ui.platform.LocalClipboardManager\n",
    "import androidx.compose.ui.platform.LocalClipboardManager\n"
    "import androidx.compose.ui.platform.LocalContext\n",
)
replace_once(
    dialog,
    "import eu.blackserv.clientssh.ssh.HostKeyTrustRequest\n",
    "import eu.blackserv.clientssh.ssh.HostKeyTrustRequest\n"
    "import eu.blackserv.clientssh.ssh.SshKnownHostsStore\n",
)
replace_once(
    dialog,
    "    val clipboard = LocalClipboardManager.current\n",
    "    val clipboard = LocalClipboardManager.current\n"
    "    val context = LocalContext.current\n"
    "    val resetFailed = remember(request.id) { mutableStateOf(false) }\n",
)
replace_once(
    dialog,
    "                    PremiumActionButton(\n                        text = if (changed) \"ZAMKNIJ\" else \"ANULUJ\",\n",
    "                    if (changed) {\n"
    "                        Text(\n"
    "                            \"Jeśli potwierdziłeś nowy fingerprint niezależnym kanałem, usuń zapis wyłącznie dla tego hosta i portu. Przy następnym połączeniu aplikacja ponownie poprosi o jawne zaufanie.\",\n"
    "                            color = tokens.muted,\n"
    "                            style = MaterialTheme.typography.labelSmall,\n"
    "                        )\n"
    "                        PremiumActionButton(\n"
    "                            text = \"USUŃ STARY KLUCZ DLA TEGO PORTU\",\n"
    "                            icon = Icons.Default.DeleteForever,\n"
    "                            onClick = {\n"
    "                                val removed = SshKnownHostsStore.forget(\n"
    "                                    context.applicationContext,\n"
    "                                    request.host,\n"
    "                                    request.port,\n"
    "                                )\n"
    "                                resetFailed.value = !removed\n"
    "                                if (removed) onReject()\n"
    "                            },\n"
    "                            modifier = Modifier.fillMaxWidth(),\n"
    "                            secondary = true,\n"
    "                        )\n"
    "                        if (resetFailed.value) {\n"
    "                            Text(\n"
    "                                \"Nie udało się usunąć starego wpisu. Zamknij aplikację i spróbuj ponownie.\",\n"
    "                                color = tokens.danger,\n"
    "                                style = MaterialTheme.typography.labelSmall,\n"
    "                            )\n"
    "                        }\n"
    "                    }\n\n"
    "                    PremiumActionButton(\n                        text = if (changed) \"ZAMKNIJ\" else \"ANULUJ\",\n",
)

# 5. Regression tests for shared hostnames on different ports.
port_test = root / "app/src/test/java/eu/blackserv/clientssh/ssh/PortScopedHostKeyRepositoryTest.kt"
port_test.write_text('''package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PortScopedHostKeyRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `same hostname on different ports has independent host keys`() {
        val knownHosts = temporaryFolder.newFile("known_hosts")
        val firstKey = ed25519KeyBlob(1)
        val secondKey = ed25519KeyBlob(101)
        val writer = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        writer.hostKeyRepository.add(HostKey(sshHostKeyAlias("blackserv.eu", 3377), firstKey), null)
        writer.hostKeyRepository.add(HostKey(sshHostKeyAlias("blackserv.eu", 3388), secondKey), null)

        val reader = JSch().apply { setKnownHosts(knownHosts.absolutePath) }
        val first = PortScopedHostKeyRepository(reader.hostKeyRepository, "blackserv.eu", 3377)
        val second = PortScopedHostKeyRepository(reader.hostKeyRepository, "blackserv.eu", 3388)

        assertEquals(HostKeyRepository.OK, first.check("blackserv.eu", firstKey))
        assertEquals(HostKeyRepository.CHANGED, first.check("blackserv.eu", secondKey))
        assertEquals(HostKeyRepository.OK, second.check("blackserv.eu", secondKey))
        assertEquals(HostKeyRepository.CHANGED, second.check("blackserv.eu", firstKey))
    }

    @Test
    fun `endpoint aliases include port and normalize brackets`() {
        assertEquals("[blackserv.eu]:22", sshHostKeyAlias("blackserv.eu", 22))
        assertEquals("[blackserv.eu]:3377", sshHostKeyAlias("[blackserv.eu]", 3377))
        assertNotEquals(sshHostKeyAlias("blackserv.eu", 3377), sshHostKeyAlias("blackserv.eu", 3388))
    }

    private fun ed25519KeyBlob(seed: Int): ByteArray {
        val output = ByteArrayOutputStream()
        writeSshString(output, "ssh-ed25519".toByteArray(Charsets.US_ASCII))
        writeSshString(output, ByteArray(32) { index -> (seed + index).toByte() })
        return output.toByteArray()
    }

    private fun writeSshString(output: ByteArrayOutputStream, value: ByteArray) {
        output.write((value.size ushr 24) and 0xff)
        output.write((value.size ushr 16) and 0xff)
        output.write((value.size ushr 8) and 0xff)
        output.write(value.size and 0xff)
        output.write(value)
    }
}
''')

store_test = root / "app/src/test/java/eu/blackserv/clientssh/ssh/SshKnownHostsStoreTest.kt"
replace_once(
    store_test,
    "        assertTrue(directory.resolve(\".explicit-host-key-verification-v1\").isFile)\n",
    "        assertTrue(directory.resolve(\".explicit-host-key-verification-v1\").isFile)\n"
    "        assertTrue(directory.resolve(\".host-key-port-scope-v2\").isFile)\n",
)
replace_once(
    store_test,
    "    @Test\n    fun `known hosts directory in place of file is rejected`() {\n",
    "    @Test\n"
    "    fun `upgrades v1 repository by archiving ambiguous host-only entries`() {\n"
    "        val directory = temporaryFolder.newFolder(\"ssh\")\n"
    "        directory.resolve(\".explicit-host-key-verification-v1\").writeText(\"v1\\n\")\n"
    "        directory.resolve(\"known_hosts\").writeText(\"blackserv.eu ssh-ed25519 AAAAOLD\\n\")\n\n"
    "        val prepared = SshKnownHostsStore.prepareDirectory(directory)\n\n"
    "        assertEquals(0L, prepared.length())\n"
    "        assertEquals(\n"
    "            \"blackserv.eu ssh-ed25519 AAAAOLD\\n\",\n"
    "            directory.resolve(\"known_hosts.pre-port-scope-v2\").readText(),\n"
    "        )\n"
    "        assertTrue(directory.resolve(\".host-key-port-scope-v2\").isFile)\n"
    "    }\n\n"
    "    @Test\n    fun `known hosts directory in place of file is rejected`() {\n",
)

# 6. Version and release note.
build = root / "app/build.gradle.kts"
replace_once(build, "        versionCode = 50\n", "        versionCode = 51\n")
replace_once(build, '        versionName = "0.3.7"\n', '        versionName = "0.3.8"\n')

note = root / "docs/HOST_KEY_PORT_SCOPING_0.3.8.md"
note.write_text('''# Client SSH 0.3.8 — rozdzielenie kluczy hosta według portu

- Każdy endpoint jest identyfikowany jako `[host]:port`.
- `blackserv.eu:22`, `blackserv.eu:3377` i `blackserv.eu:3388` mogą mieć różne klucze hosta bez fałszywego alarmu.
- Terminal, SFTP i telemetria korzystają z tej samej tożsamości endpointu.
- Przy pierwszym uruchomieniu 0.3.8 stare, niejednoznaczne wpisy host-only są archiwizowane i aktywny magazyn jest jednorazowo czyszczony.
- Każdy endpoint wymaga jednego ponownego porównania fingerprintu.
- Przy prawdziwej zmianie klucza użytkownik może usunąć wyłącznie stary wpis dla konkretnego hosta i portu; nowe połączenie nadal wymaga jawnego zaufania po weryfikacji.
- Nie dodano trybu `StrictHostKeyChecking=no` ani automatycznego akceptowania zmienionych kluczy.
''')

print("Applied Client SSH v0.3.8 host-key port scoping fix")
