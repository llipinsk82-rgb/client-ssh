package eu.blackserv.clientssh.model

import java.util.UUID

enum class ConnectionProtocol(val label: String, val defaultPort: Int) {
    SSH("SSH", 22),
    TELNET("Telnet", 23),
}

enum class AuthenticationMethod(val label: String) {
    PASSWORD("Hasło"),
    PRIVATE_KEY("Klucz prywatny"),
    INTERACTIVE("Ręcznie"),
}

data class HostProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val protocol: ConnectionProtocol,
    val authenticationMethod: AuthenticationMethod,
)

data class FavoriteCommand(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val command: String,
    val runImmediately: Boolean = false,
)

enum class TextWrapMode(val label: String) {
    WRAP("Zawijaj"),
    NO_WRAP("Bez zawijania"),
}
