package eu.blackserv.clientssh.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import eu.blackserv.clientssh.model.HostProfile
import eu.blackserv.clientssh.model.SshCompatibilityMode

/**
 * Deprecated algorithms are activated only after the user explicitly enables
 * the legacy Enigma2/Dropbear mode in at least one profile. Secure algorithms
 * remain first in the global proposal, while the selected profile also gets an
 * explicit per-session policy with legacy RSA first for old Dropbear auth.
 */
internal fun ensureLegacySshAlgorithmsAvailable() {
    if (legacyAlgorithmsBootstrapped) return
    synchronized(legacyBootstrapLock) {
        if (legacyAlgorithmsBootstrapped) return
        appendGlobalAlgorithms("server_host_key", LEGACY_HOST_KEY_ALGORITHMS)
        appendGlobalAlgorithms("PubkeyAcceptedAlgorithms", LEGACY_PUBLIC_KEY_ALGORITHMS)
        appendGlobalAlgorithms("kex", LEGACY_KEX_ALGORITHMS)
        appendGlobalAlgorithms("cipher.c2s", LEGACY_CIPHERS)
        appendGlobalAlgorithms("cipher.s2c", LEGACY_CIPHERS)
        appendGlobalAlgorithms("mac.c2s", LEGACY_MACS)
        appendGlobalAlgorithms("mac.s2c", LEGACY_MACS)
        legacyAlgorithmsBootstrapped = true
    }
}

internal fun Session.applyProfileSshCompatibility(profile: HostProfile) {
    if (profile.sshCompatibilityMode != SshCompatibilityMode.LEGACY_ENIGMA2) return

    ensureLegacySshAlgorithmsAvailable()
    prependAlgorithms("server_host_key", LEGACY_HOST_KEY_ALGORITHMS)
    prependAlgorithms("PubkeyAcceptedAlgorithms", LEGACY_PUBLIC_KEY_ALGORITHMS)
    appendAlgorithms("kex", LEGACY_KEX_ALGORITHMS)
    appendAlgorithms("cipher.c2s", LEGACY_CIPHERS)
    appendAlgorithms("cipher.s2c", LEGACY_CIPHERS)
    appendAlgorithms("mac.c2s", LEGACY_MACS)
    appendAlgorithms("mac.s2c", LEGACY_MACS)
}

internal fun HostProfile.requiresLegacySshCompatibility(): Boolean =
    sshCompatibilityMode == SshCompatibilityMode.LEGACY_ENIGMA2

private fun Session.prependAlgorithms(key: String, legacy: List<String>) {
    val current = getConfig(key).orEmpty().splitAlgorithms()
    setConfig(key, (legacy + current).distinct().joinToString(","))
}

private fun Session.appendAlgorithms(key: String, legacy: List<String>) {
    val current = getConfig(key).orEmpty().splitAlgorithms()
    setConfig(key, (current + legacy).distinct().joinToString(","))
}

private fun appendGlobalAlgorithms(key: String, legacy: List<String>) {
    val current = JSch.getConfig(key).orEmpty().splitAlgorithms()
    JSch.setConfig(key, (current + legacy).distinct().joinToString(","))
}

private fun String.splitAlgorithms(): List<String> =
    split(',').map(String::trim).filter(String::isNotBlank)

@Volatile
private var legacyAlgorithmsBootstrapped = false
private val legacyBootstrapLock = Any()

internal val LEGACY_HOST_KEY_ALGORITHMS = listOf(
    "ssh-rsa",
    "ssh-dss",
)

internal val LEGACY_PUBLIC_KEY_ALGORITHMS = listOf(
    "ssh-rsa",
    "ssh-dss",
)

internal val LEGACY_KEX_ALGORITHMS = listOf(
    "diffie-hellman-group14-sha1",
    "diffie-hellman-group-exchange-sha1",
    "diffie-hellman-group1-sha1",
)

internal val LEGACY_CIPHERS = listOf(
    "aes128-cbc",
    "aes192-cbc",
    "aes256-cbc",
    "3des-cbc",
)

internal val LEGACY_MACS = listOf(
    "hmac-sha1",
    "hmac-sha1-96",
    "hmac-md5",
    "hmac-md5-96",
)
