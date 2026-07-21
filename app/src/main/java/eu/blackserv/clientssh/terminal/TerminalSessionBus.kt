package eu.blackserv.clientssh.terminal

import eu.blackserv.clientssh.model.HostProfile
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TerminalConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

data class TerminalSnapshot(
    val profileId: String? = null,
    val profileName: String = "",
    val state: TerminalConnectionState = TerminalConnectionState.IDLE,
    val statusText: String = "Rozłączono",
    val output: String = "",
)

object PendingSessionRegistry {
    private val profiles = ConcurrentHashMap<String, HostProfile>()

    fun put(profile: HostProfile) {
        profiles[profile.id] = profile
    }

    fun get(profileId: String): HostProfile? = profiles[profileId]

    fun remove(profileId: String) {
        profiles.remove(profileId)
    }
}

object TerminalSessionBus {
    private const val MAX_BUFFER_CHARS = 1_000_000
    private val clearScreenPattern = Regex("(?:\\u001Bc|\\u001B\\[(?:2J|3J))")

    private val _snapshot = MutableStateFlow(TerminalSnapshot())
    val snapshot = _snapshot.asStateFlow()

    @Volatile
    private var writer: ((ByteArray) -> Unit)? = null

    fun begin(profile: HostProfile) {
        writer = null
        _snapshot.value = TerminalSnapshot(
            profileId = profile.id,
            profileName = profile.name,
            state = TerminalConnectionState.CONNECTING,
            statusText = "Łączenie z ${profile.host}:${profile.port}…",
            output = "Łączenie z ${profile.username}@${profile.host}:${profile.port}…\n",
        )
    }

    fun attachWriter(sendBytes: (ByteArray) -> Unit) {
        writer = sendBytes
    }

    fun detachWriter() {
        writer = null
    }

    fun markConnected(text: String = "Połączono") {
        _snapshot.update {
            it.copy(
                state = TerminalConnectionState.CONNECTED,
                statusText = text,
            )
        }
    }

    fun markDisconnected(text: String = "Sesja zakończona") {
        writer = null
        _snapshot.update {
            it.copy(
                state = TerminalConnectionState.DISCONNECTED,
                statusText = text,
            )
        }
    }

    fun markError(message: String) {
        writer = null
        append("\n[Błąd] $message\n")
        _snapshot.update {
            it.copy(
                state = TerminalConnectionState.ERROR,
                statusText = message,
            )
        }
    }

    fun append(bytes: ByteArray, length: Int = bytes.size) {
        append(String(bytes, 0, length, StandardCharsets.UTF_8))
    }

    fun append(text: String) {
        if (text.isEmpty()) return
        _snapshot.update { current ->
            val combined = current.output + text
            val lastClear = clearScreenPattern.findAll(combined).lastOrNull()
            val afterTerminalClear = if (lastClear != null) {
                combined.substring(lastClear.range.last + 1)
            } else {
                combined
            }
            current.copy(
                output = if (afterTerminalClear.length > MAX_BUFFER_CHARS) {
                    afterTerminalClear.takeLast(MAX_BUFFER_CHARS)
                } else {
                    afterTerminalClear
                },
            )
        }
    }

    fun send(text: String) {
        send(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun send(bytes: ByteArray) {
        val sink = writer
        if (sink == null) {
            append("\n[Sesja nie jest połączona]\n")
            return
        }
        sink(bytes)
    }

    fun clearLocalBuffer() {
        _snapshot.update { it.copy(output = "") }
    }
}
