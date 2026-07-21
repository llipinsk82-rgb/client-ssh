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
    if (!normalized.contains("PRIVATE KEY")) {
        return "Plik nie wygląda jak klucz prywatny SSH."
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
            message.contains("passphrase", ignoreCase = true) -> "Nieprawidłowe hasło klucza prywatnego."
            message.contains("invalid privatekey", ignoreCase = true) -> "Nieobsługiwany albo uszkodzony klucz prywatny."
            else -> message.ifBlank { "Nie udało się odczytać klucza prywatnego." }
        }
    }
}
