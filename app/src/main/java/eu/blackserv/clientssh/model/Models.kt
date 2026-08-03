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

enum class SshCompatibilityMode(
    val label: String,
    val description: String,
) {
    MODERN(
        label = "Nowoczesny SSH",
        description = "Bezpieczne algorytmy dla aktualnych serwerów OpenSSH i Dropbear.",
    ),
    LEGACY_ENIGMA2(
        label = "Stary tuner / Enigma2",
        description = "Włącza starsze algorytmy wyłącznie dla tego profilu i starego Dropbear.",
    ),
}

enum class AppSkin(
    val label: String,
    val description: String,
    val selectable: Boolean,
) {
    SAPPHIRE(
        label = "Sapphire",
        description = "Oficjalny niebieski motyw Client SSH.",
        selectable = true,
    ),
    AURORA(
        label = "Aurora",
        description = "Turkusowy motyw premium inspirowany zorzą.",
        selectable = true,
    ),
    OBSIDIAN(
        label = "Obsidian",
        description = "Ciemny grafitowo-fioletowy motyw premium.",
        selectable = true,
    ),

    // Zachowane wyłącznie do bezpiecznej migracji wcześniejszych preferencji.
    // Nie są pokazywane w ustawieniach i zawsze renderują Sapphire.
    GRAPHITE(
        label = "Legacy Graphite",
        description = "Starszy zapis motywu — automatycznie używa Sapphire.",
        selectable = false,
    ),
    NEON(
        label = "Legacy Neon",
        description = "Starszy zapis motywu — automatycznie używa Sapphire.",
        selectable = false,
    );

    val canonical: AppSkin
        get() = when (this) {
            GRAPHITE, NEON -> SAPPHIRE
            else -> this
        }

    companion object {
        val selectableEntries: List<AppSkin> = entries.filter { it.selectable }
    }
}

data class AppSettings(
    val skin: AppSkin = AppSkin.SAPPHIRE,
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
    val sshCompatibilityMode: SshCompatibilityMode = SshCompatibilityMode.MODERN,
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
    WRAP("AUTO ZAWIJANIE"),
    NO_WRAP("BEZ ZAWIJANIA"),
}
