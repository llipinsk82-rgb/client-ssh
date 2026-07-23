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

enum class AppSkin(
    val label: String,
    val description: String,
) {
    GRAPHITE(
        label = "BlackServ Classic",
        description = "Prosty, spokojny i czytelny wygląd do codziennej pracy.",
    ),
    NEON(
        label = "BlackServ Neon",
        description = "Ciemniejszy wygląd z mocniejszą zielenią, cyanem i efektem command deck.",
    ),
}

data class AppSettings(
    val skin: AppSkin = AppSkin.GRAPHITE,
)

data class HostProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val protocol: ConnectionProtocol,
    val authenticationMethod: AuthenticationMethod,
    val password: String = "",
    val privateKey: String = "",
    val privateKeyPassphrase: String = "",
)

data class FavoriteCommand(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val command: String,
    val runImmediately: Boolean = false,
)

data class TerminalSettings(
    val keepScreenAwake: Boolean = true,
    val backgroundSessionEnabled: Boolean = true,
)

fun defaultFavoriteCommands(): List<FavoriteCommand> = listOf(
    FavoriteCommand(name = "clear", command = "clear", runImmediately = true),
    FavoriteCommand(name = "sudo -i", command = "sudo -i", runImmediately = true),
)

enum class TextWrapMode(val label: String) {
    WRAP("Zawijaj"),
    NO_WRAP("Bez zawijania"),
}
