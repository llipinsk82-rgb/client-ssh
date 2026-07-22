# Client SSH

BlackServ Client SSH to lekki klient administracyjny dla Androida przeznaczony do codziennej obsługi VPS-ów i tunerów Enigma2.

## Wersja 0.3.0

Najważniejszy cel tej wersji: dopracowanie wyglądu po wdrożeniu OTA i realnego SFTP.

- prawdziwa sesja SSH z PTY,
- logowanie hasłem,
- logowanie kluczem prywatnym przez wklejenie lub wybór pliku,
- obsługa OpenSSH, PEM, PKCS#8 i PuTTY PPK,
- trwały zapis profili hostów po restarcie i aktualizacji,
- sekrety profili są zapisywane przez Android Keystore,
- gotowe komendy `clear` i `sudo -i` są edytowalnymi ulubionymi komendami,
- kolorowy terminal z podstawową obsługą ANSI,
- Ctrl+C, Ctrl+D, Tab, Esc i strzałki,
- `Ctrl+D` / `exit` zamyka ekran terminala po zakończeniu powłoki,
- foreground service i keepalive,
- kopiowanie bufora i zapis logów,
- terminal obsługuje pinch-to-zoom dwoma palcami dla rozmiaru czcionki,
- OTA z GitHub Releases z wyborem najnowszej wersji nowszej od lokalnej,
- weryfikacja pobranego APK przez SHA-256 z assetu release,
- realny ekran SFTP dla profili SSH,
- SFTP używa tego samego hasła albo klucza co profil terminala,
- SFTP obsługuje przeglądanie katalogów, przejście wyżej i odświeżanie,
- SFTP obsługuje pobieranie i wysyłanie plików przez systemowe okna Androida,
- SFTP obsługuje tworzenie katalogów, zmianę nazwy oraz usuwanie plików i pustych katalogów,
- SFTP ma kompaktowy widok w stylu Total Commander: cienkie wiersze zamiast dużych kart,
- akcje SFTP są w menu pozycji: otwórz/pobierz, zmień nazwę, usuń,
- ekran główny pokazuje numer wersji aplikacji,
- UI poza terminalem ma nową paletę BlackServ: graphite, terminal green i subtelny amber bez jaskrawego niebieskiego,
- stały podpis APK dla kolejnych aktualizacji.

## Nadal do wykonania

- realny Health Monitor,
- dalsze dopracowanie SFTP po testach na telefonie,
- ewentualne trwałe ustawienie rozmiaru czcionki terminala po ustabilizowaniu zoomu.

## Aktualizacje

Wydania po połączeniu zmian z gałęzią `main` są automatycznie budowane jako podpisany APK i publikowane w GitHub Releases. Aplikacja pobiera listę wydań, wybiera najnowsze wydanie nowsze od zainstalowanej wersji, pobiera podpisany APK, sprawdza SHA-256 i przekazuje plik systemowemu instalatorowi Androida.

## Dokumentacja ciągłości projektu

Szczegółowy stan projektu, historia wydań i lista następnych kroków są w `docs/PROJECT_STATE.md`. Ten plik ma umożliwiać start nowego czatu dokładnie z miejsca, w którym skończył poprzedni.
