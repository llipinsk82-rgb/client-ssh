package eu.blackserv.clientssh.terminal

import com.jcraft.jsch.JSch
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

data class PrivateKeyInfo(
    val algorithm: String,
    val fingerprint: String,
    val comment: String?,
) {
    val suggestedUsername: String?
        get() = comment
            ?.substringBefore('@')
            ?.trim()
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
}

fun String.normalizePrivateKeyText(): String =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .plus("\n")

fun inspectPrivateKeyMaterial(keyText: String, passphrase: String): Result<PrivateKeyInfo> = runCatching {
    val normalized = keyText.normalizePrivateKeyText()
    val firstLine = normalized.lineSequence().firstOrNull().orEmpty()
    val looksLikePutty = firstLine.startsWith("PuTTY-User-Key-File-", ignoreCase = true)
    val looksLikePemOrOpenSsh = normalized.contains("PRIVATE KEY")

    require(looksLikePutty || looksLikePemOrOpenSsh) {
        "Plik nie wygląda jak prywatny klucz SSH, OpenSSH ani PuTTY PPK."
    }

    val jsch = JSch()
    try {
        jsch.addIdentity(
            "validation",
            normalized.toByteArray(StandardCharsets.UTF_8),
            null,
            passphrase.takeIf(String::isNotEmpty)?.toByteArray(StandardCharsets.UTF_8),
        )

        val identity = jsch.identityRepository.identities.firstOrNull()
            ?: error("Nie udało się odczytać tożsamości z klucza.")
        val publicKey = identity.publicKeyBlob
            ?: error("Klucz nie zawiera części publicznej potrzebnej do uwierzytelnienia.")
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
        val fingerprint = Base64.getEncoder().withoutPadding().encodeToString(digest)
        val comment = normalized.lineSequence()
            .firstOrNull { it.startsWith("Comment:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        PrivateKeyInfo(
            algorithm = identity.algName.ifBlank { "SSH" },
            fingerprint = "SHA256:$fingerprint",
            comment = comment,
        )
    } finally {
        jsch.removeAllIdentity()
    }
}

fun validatePrivateKeyMaterial(keyText: String, passphrase: String): String? =
    inspectPrivateKeyMaterial(keyText, passphrase).exceptionOrNull()?.let { error ->
        val message = error.message.orEmpty()
        when {
            message.contains("passphrase", ignoreCase = true) ||
                message.contains("MAC Error", ignoreCase = true) ->
                "Nieprawidłowe hasło klucza prywatnego albo uszkodzony plik PPK."
            message.contains("invalid privatekey", ignoreCase = true) ->
                "Nieobsługiwany albo uszkodzony klucz prywatny."
            else -> message.ifBlank { "Nie udało się odczytać klucza prywatnego." }
        }
    }
