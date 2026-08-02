package eu.blackserv.clientssh.service

internal fun Throwable.isRetryableSshError(): Boolean {
    val raw = message.orEmpty().lowercase()
    return listOf(
        "auth fail",
        "authentication",
        "invalid privatekey",
        "invalid private key",
        "reject hostkey",
        "host key has changed",
        "hostkey has been changed",
        "host jest pusty",
        "użytkownik ssh jest pusty",
        "klucz prywatny jest pusty",
    ).none(raw::contains)
}

internal fun Throwable.toSafeSshMessage(host: String): String {
    val raw = message?.trim().orEmpty()
    return when {
        raw.contains("Auth fail", ignoreCase = true) ||
            raw.contains("authentication", ignoreCase = true) ->
            "Nieprawidłowy login, hasło lub klucz SSH."

        raw.contains("invalid privatekey", ignoreCase = true) ||
            raw.contains("invalid private key", ignoreCase = true) ->
            "Nieobsługiwany albo uszkodzony klucz prywatny."

        raw.contains("klucz prywatny jest pusty", ignoreCase = true) ->
            "Klucz prywatny jest pusty. Edytuj profil i wklej poprawny klucz."

        raw.contains("host key has changed", ignoreCase = true) ||
            raw.contains("hostkey has been changed", ignoreCase = true) ->
            "Klucz hosta SSH zmienił się. Połączenie zostało zablokowane."

        raw.contains("reject HostKey", ignoreCase = true) ||
            raw.contains("unknownhostkey", ignoreCase = true) ->
            "Klucz hosta SSH nie został zaakceptowany. Połączenie zostało zablokowane."

        raw.contains("UnknownHostException", ignoreCase = true) ||
            raw.contains("Unable to resolve host", ignoreCase = true) ->
            "Nie można znaleźć hosta: $host. Sprawdź internet, DNS albo literówkę w profilu."

        raw.contains("timeout", ignoreCase = true) ->
            "Przekroczono czas oczekiwania na połączenie."

        raw.contains("connection refused", ignoreCase = true) ->
            "Serwer odrzucił połączenie. Sprawdź host, port i usługę SSH."

        raw.contains("Host jest pusty", ignoreCase = true) ->
            "Host jest pusty. Edytuj profil i wpisz domenę albo IP."

        raw.contains("Użytkownik SSH jest pusty", ignoreCase = true) ->
            "Użytkownik SSH jest pusty. Edytuj profil i wpisz login."

        else -> {
            val errorType = javaClass.simpleName.takeIf(String::isNotBlank) ?: "błąd transportu"
            "Nie udało się nawiązać połączenia SSH ($errorType)."
        }
    }
}
