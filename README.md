# Client SSH

BlackServ Client SSH to lekki klient administracyjny dla Androida przeznaczony do codziennej obsługi VPS-ów i tunerów Enigma2.

## Wersja 0.2.3

- prawdziwa sesja SSH z PTY,
- logowanie hasłem,
- logowanie kluczem prywatnym przez wklejenie lub wybór pliku,
- obsługa OpenSSH, PEM, PKCS#8 i PuTTY PPK,
- poprawione `clear` i usuwanie fałszywego promptu z sekwencji OSC,
- kolorowy terminal z podstawową obsługą ANSI,
- `clear`, Ctrl+C, Ctrl+D, Tab, Esc i strzałki,
- `Ctrl+D` / `exit` zamyka ekran terminala po zakończeniu powłoki,
- ulubione komendy można przesuwać i pokazywać na pasku skrótów,
- foreground service i keepalive,
- kopiowanie bufora i zapis logów,
- aktualizacje OTA z GitHub Releases,
- stały podpis APK dla kolejnych aktualizacji,
- ciemny interfejs BlackServ.

## Aktualizacje

Wydania po połączeniu zmian z gałęzią `main` są automatycznie budowane jako podpisany APK i publikowane w GitHub Releases. Aplikacja sprawdza najnowszy Release, pobiera APK i przekazuje go systemowemu instalatorowi Androida.
