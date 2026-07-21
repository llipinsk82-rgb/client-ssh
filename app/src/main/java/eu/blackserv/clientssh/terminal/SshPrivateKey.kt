package eu.blackserv.clientssh.terminal

import com.jcraft.jsch.JSch
import java.nio.charset.StandardCharsets

fun String.normalizePrivateKeyText(): String =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .plus("\n")

fun validatePrivateKeyMaterial(keyText: String, passphrase: String): String? {
    val normalized = keyText.normalizePrivateKeyText()
    val firstLine = normalized.lineSequence().firstOrNull().orEmpty()
    val looksLikePutty = firstLine.startsWith("PuTTY-User-Key-File-", ignoreCase = true)
    val looksLikePemOrOpenSsh = normalized.contains("PRIVATE KEY")

    if (!looksLikePutty && !looksLikePemOrOpenSsh) {
        return "Plik nie wygląda jak prywatny klucz SSH, OpenSSH ani PuTTY PPK."
    }

    return runCatching {
        val jsch = JSch()
        jsch.addIdentity(
            "validation",
            normalized.toByteArray(StandardCharsets.UTF_8),
            null,
            passphrase.takeIf(String::isNotEmpty)?.toByteArray(StandardCharsets.UTF_8),
        )
        jsch.removeAllIdentity()
    }.exceptionOrNull()?.let { error ->
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
}
