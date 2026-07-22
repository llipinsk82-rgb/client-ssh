# Client SSH

BlackServ Client SSH to lekki klient administracyjny dla Androida przeznaczony do codziennej obsługi VPS-ów i tunerów Enigma2.

## Wersja 0.2.4

- prawdziwa sesja SSH z PTY,
- logowanie hasłem,
- logowanie kluczem prywatnym przez wklejenie lub wybór pliku,
- obsługa OpenSSH, PEM, PKCS#8 i PuTTY PPK,
- trwały zapis profili hostów po restarcie i aktualizacji,
- trwały zapis ulubionych komend oraz ich kolejności,
- sekrety profili są zapisywane przez Android Keystore,
- gotowe komendy `clear` i `sudo -i` są edytowalnymi ulubionymi komendami,
- poprawione `clear` i usuwanie fałszywego promptu z sekwencji OSC,
- kolorowy terminal z podstawową obsługą ANSI,
- Ctrl+C, Ctrl+D, Tab, Esc i strzałki,
- `Ctrl+D` / `exit` zamyka ekran terminala po zakończeniu powłoki,
- przełącznik `Ekran ON/OFF`, żeby ekran nie gasł podczas długich komend,
- foreground service i keepalive,
- kopiowanie bufora i zapis logów,
- aktualizacje OTA z GitHub Releases,
- stały podpis APK dla kolejnych aktualizacji,
- ciemny interfejs BlackServ.

## Aktualizacje

Wydania po połączeniu zmian z gałęzią `main` są automatycznie budowane jako podpisany APK i publikowane w GitHub Releases. Aplikacja sprawdza najnowszy Release, pobiera APK i przekazuje go systemowemu instalatorowi Androida.
