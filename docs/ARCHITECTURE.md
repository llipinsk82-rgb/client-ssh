# Architektura

## Warstwy docelowe

- `core/terminal` — parser terminala, bufor linii, ANSI i klawisze sterujące.
- `core/ssh` — sesja PTY, keepalive, hasło i klucze prywatne.
- `core/telnet` — negocjacja Telnet i interaktywny terminal.
- `core/sftp` — operacje plikowe przez aktywną sesję SSH.
- `data` — profile, ulubione, ustawienia i bezpieczne odwołania do poświadczeń.
- `service` — trwała sesja w foreground service.
- `ui` — Compose, pełny ekran, zawijanie tekstu i obsługa małego ekranu.

## Zasady

1. Zamknięcie Activity nie może automatycznie kończyć sesji.
2. `exit` i `Ctrl+D` zamykają ekran dopiero po rzeczywistym zamknięciu kanału.
3. Bufor widoku jest ograniczony, pełny log jest dopisywany strumieniowo do pliku.
4. Port jest częścią profilu i nigdy nie jest zakodowany na sztywno.
5. Sekrety nie trafiają do modeli UI, logów ani repozytorium.
