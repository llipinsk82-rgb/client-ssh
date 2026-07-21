# Client SSH

BlackServ Client SSH to lekki klient administracyjny dla Androida przeznaczony do codziennej obsługi VPS-ów i tunerów Enigma2.

## Wersja 0.2.2

- prawdziwa sesja SSH z PTY,
- logowanie hasłem,
- logowanie kluczem prywatnym przez wklejenie lub wybór pliku,
- obsługa OpenSSH, PEM, PKCS#8 i PuTTY PPK,
- `clear`, Ctrl+C, Ctrl+D, Tab, Esc i strzałki,
- foreground service i keepalive,
- kopiowanie bufora i zapis logów,
- aktualizacje z GitHub Releases,
- stały podpis APK dla kolejnych aktualizacji,
- ciemny interfejs BlackServ.

## Aktualizacje

Wydania po połączeniu zmian z gałęzią `main` są automatycznie budowane jako podpisany APK i publikowane w GitHub Releases. Aplikacja sprawdza najnowszy Release, pobiera APK i przekazuje go systemowemu instalatorowi Androida.

Wersje 0.1.0–0.2.1 były podpisywane tymczasowymi kluczami CI. Przejście na 0.2.2 wymaga jednorazowego odinstalowania starej wersji. Od 0.2.2 kolejne APK używają tego samego podpisu i instalują się jako zwykła aktualizacja.

## Budowanie

Projekt używa:

- Android SDK 36,
- JDK 17,
- Gradle 9.5.0,
- Jetpack Compose,
- mwiede JSch.

GitHub Actions buduje `:app:assembleDebug`. Workflow wydaniowy buduje `:app:assembleRelease` i publikuje plik `client-ssh-<wersja>.apk`.

## Status

SSH działa. Telnet, pełne SFTP, trwałe profile i Android Keystore są kolejnymi etapami.
