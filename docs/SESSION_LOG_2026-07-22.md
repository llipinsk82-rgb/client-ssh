# Session log — 2026-07-22

Sanityzowany log ciągłości projektu. Nie jest to surowa kopia czatu. Surowych screenów, hostów, portów, loginów ani prywatnych danych nie publikujemy w repo.

## Uczestnicy i styl pracy

- Użytkownik: Łukasz.
- Asystent w projekcie: Ezra.
- Styl: luźny, konkretny, bez biurowego bełkotu.
- Komenda `go` oznacza akceptację pomysłu/projektu i wykonanie.
- Po zwykłym pytaniu Ezra odpowiada i wraca do pracy, jeśli jest co robić.
- Wyjątek: tematy krytyczne bezpieczeństwa — wtedy stop i czekamy na decyzję Łukasza.

## Zakres wykonany w tej części rozmowy

### 0.2.x — stabilizacja podstaw

- Poprawiono trwały zapis profili, ulubionych i ustawień terminala.
- Dodano Android Keystore dla sekretów profili.
- Dodano keep screen awake.
- Poprawiono przyciski ulubionych komend: edycja, przesuwanie, usuwanie.
- Dodano klonowanie profili hostów.
- Przeniesiono przełącznik niewygaszania ekranu z konfliktowego miejsca w UI.
- Utwardzono OTA updater.

### 0.2.8 — OTA

- Updater pobiera listę GitHub Releases.
- Wybiera najnowszą wersję nowszą od lokalnej.
- Preferuje release APK.
- Pobiera `.sha256` i weryfikuje pobrany APK.
- Czytelniejsze komunikaty błędów dla GitHub/Android/install.

### 0.2.9 — realny SFTP

- Dodano realny `SftpClient` na JSch / `ChannelSftp`.
- SFTP używa profilu SSH oraz tego samego hasła albo klucza.
- Obsługuje listę katalogów, wejście w folder, wyjście wyżej, odświeżenie.
- Upload/download przez systemowe pickery Androida.
- Tworzenie katalogów, zmiana nazwy, usuwanie plików i pustych folderów.

### 0.3.0 — UI + kompakt SFTP

- Dodano nowy kierunek wizualny BlackServ: graphite / terminal green / amber.
- Ekran główny pokazuje wersję aplikacji.
- SFTP dostał kompaktowy widok w stylu Total Commander.
- Akcje SFTP przeniesione pod menu pozycji.
- Terminal dostał pinch-to-zoom czcionki.
- Dodano dokumentację ciągłości projektu.

### 0.3.1 — premium UI pass

Po feedbacku Łukasza, że 0.3.0 zmieniło głównie kolory, a miało mieć więcej klasy, dodano:

- warstwowe panele hostów,
- cienie,
- cienkie linie i highlighty,
- boczne akcenty,
- bardziej techniczne przyciski,
- mocniejszy styl commander/premium w SFTP,
- globalne shapes i paletę dopięte pod BlackServ.

### 0.3.2 — Session Keeper

Łukasz zapytał, czy aplikacja może trzymać sesję po wyjściu/zamknięciu ekranu aplikacji.

Wdrożono:

- terminal SSH nie rozłącza się po zwykłym wyjściu z ekranu przez `Wstecz`,
- sesja działa dalej w foreground service,
- powiadomienie: `Client SSH działa w tle`,
- akcja `Rozłącz` w powiadomieniu,
- kliknięcie powiadomienia otwiera aplikację z intencją powrotu do aktywnej sesji,
- SFTP pozostaje sesją ekranową i nadal rozłącza się po wyjściu z ekranu SFTP.

## Feedback wizualny

Łukasz pokazał kierunek wizualny typu neon command deck:

- bardzo ciemne tło,
- zielony glow jako akcent,
- amber dla SFTP,
- karty hostów z mocną głębią,
- dolna belka `Serwery / Historia / Ustawienia`,
- ustawienia jako miejsce na język, skin, font terminala, keep screen awake, OTA, eksport/import.

Decyzja kierunkowa: nie kopiować 1:1, ale zrobić jako opcjonalny skin `BlackServ Neon`. Domyślnie może zostać spokojniejszy Classic.

## Klucz SSH

Łukasz wygenerował klucz OpenSSH, ale aplikacja nadal pokazuje błąd logowania kluczem. Nie prosić o prywatny klucz.

Do wykonania:

- wykrycie typu klucza,
- fingerprint SHA256,
- public key do skopiowania,
- passphrase dla klucza,
- test auth przed zapisem profilu,
- rozróżnienie błędów: klucz nieczytelny / passphrase zły / serwer odrzucił klucz / brak public key w `authorized_keys`.

## Najbliższy plan

1. `0.3.3` — dolna belka, Historia, Ustawienia, podstawy skinów, `BlackServ Neon`.
2. `0.3.4` — diagnostyka kluczy SSH.
3. `0.3.5` — realny Monitor / Health.
4. Potem dalsze SFTP: sortowanie, breadcrumb, multi-select, show hidden, batch actions.

## Bezpieczeństwo

- Nie publikować surowej rozmowy na GH.
- Nie publikować screenów z hostami/loginami/portami w repo.
- Nie publikować prywatnych kluczy ani pełnych logów połączeń.
- Publiczna dokumentacja ma być sanityzowana.
