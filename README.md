# Client SSH

BlackServ Client SSH to lekki klient administracyjny dla Androida przeznaczony do codziennej obsługi VPS-ów i tunerów Enigma2.

## Wersja 0.2.9

- prawdziwa sesja SSH z PTY,
- logowanie hasłem,
- logowanie kluczem prywatnym przez wklejenie lub wybór pliku,
- obsługa OpenSSH, PEM, PKCS#8 i PuTTY PPK,
- trwały zapis profili hostów po restarcie i aktualizacji,
- zapis profili i ulubionych komend wykonywany synchronicznie przez `commit`,
- zapasowy zapis profili, żeby update nie kasował konfiguracji,
- odporniejsze wczytywanie profili po błędzie sekretu/Keystore,
- sekrety profili są zapisywane przez Android Keystore,
- gotowe komendy `clear` i `sudo -i` są edytowalnymi ulubionymi komendami,
- odchudzone ulubione komendy: bez `Wstaw` i `Uruchom`,
- widoczny przycisk `Usuń` dla ulubionych komend,
- przełącznik niewygaszania ekranu w pasku statusu hosta,
- dodane klonowanie profilu hosta do szybkiej zmiany portu/nazwy,
- poprawione `clear` i usuwanie fałszywego promptu z sekwencji OSC,
- kolorowy terminal z podstawową obsługą ANSI,
- Ctrl+C, Ctrl+D, Tab, Esc i strzałki,
- `Ctrl+D` / `exit` zamyka ekran terminala po zakończeniu powłoki,
- foreground service i keepalive,
- kopiowanie bufora i zapis logów,
- OTA z GitHub Releases z wyborem najnowszej wersji nowszej od lokalnej,
- weryfikacja pobranego APK przez SHA-256 z assetu release,
- czytelniejsze komunikaty OTA i obsługa zgody Androida na instalację,
- realny ekran SFTP dla profili SSH,
- SFTP używa tego samego hasła albo klucza co profil terminala,
- SFTP obsługuje przeglądanie katalogów, przejście wyżej i odświeżanie,
- SFTP obsługuje pobieranie i wysyłanie plików przez systemowe okna Androida,
- SFTP obsługuje tworzenie katalogów, zmianę nazwy oraz usuwanie plików i pustych katalogów,
- stały podpis APK dla kolejnych aktualizacji,
- ciemny interfejs BlackServ.

## Nadal do wykonania

- realny Health Monitor.

## Aktualizacje

Wydania po połączeniu zmian z gałęzią `main` są automatycznie budowane jako podpisany APK i publikowane w GitHub Releases. Aplikacja pobiera listę wydań, wybiera najnowsze wydanie nowsze od zainstalowanej wersji, pobiera podpisany APK, sprawdza SHA-256 i przekazuje plik systemowemu instalatorowi Androida.
