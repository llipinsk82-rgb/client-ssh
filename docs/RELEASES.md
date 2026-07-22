# Historia wydań Client SSH

## 0.3.2

Cel: Session Keeper — utrzymanie terminala SSH po wyjściu z ekranu aplikacji, ale jawnie, z powiadomieniem i kontrolą rozłączenia.

- Terminal SSH nie rozłącza się po zwykłym wyjściu z ekranu przez `Wstecz`.
- Sesja działa dalej w foreground service.
- Powiadomienie pokazuje `Client SSH działa w tle`.
- Powiadomienie ma akcję `Rozłącz`.
- Dotknięcie powiadomienia otwiera aplikację z intencją powrotu do aktywnej sesji.
- SFTP bez zmian: rozłącza się po wyjściu z ekranu SFTP.

## 0.3.1

Cel: drugi, właściwy pass wyglądu po uwadze, że 0.3.0 zmieniło głównie kolory, a nie dało jeszcze efektu premium.

- Hosty dostały warstwowe panele z cienkim highlightem, cieniem i bocznym akcentem.
- Przyciski są bardziej techniczne: mniej plastikowego Material, więcej obramowań i klasowego minimalizmu.
- SFTP dostał mocniejszy styl commander/premium: cienie, linie, ramki, akcent folder/plik.
- Globalne kształty i paleta zostały dopasowane do graphite / terminal green / amber.
- Transport SSH/SFTP nie był zmieniany — to wydanie jest warstwą UI/UX.

## 0.3.0

Cel: dopracowanie wyglądu i ergonomii po wdrożeniu realnego SFTP.

- Globalna paleta BlackServ: graphite, terminal green, amber.
- Ekran główny pokazuje wersję aplikacji.
- Lista hostów jest bardziej zwarta i profesjonalna.
- SFTP ma widok w stylu Total Commander: kompaktowe wiersze zamiast dużych kart.
- Akcje SFTP są w menu pozycji: otwórz/pobierz, zmień nazwę, usuń.
- Terminal ma pinch-to-zoom czcionki.
- Dodano `docs/PROJECT_STATE.md` jako punkt startowy dla kolejnych czatów.

## 0.2.9

Cel: realny SFTP.

- Dodany `SftpClient` na JSch / ChannelSftp.
- SFTP używa profilu SSH, hasła lub klucza.
- Obsługa listy katalogów, wejścia w katalog, przejścia wyżej i odświeżenia.
- Upload i download przez systemowe okna Androida.
- Tworzenie katalogów, rename, delete.

## 0.2.8

Cel: OTA.

- Updater pobiera listę GitHub Releases.
- Wybiera najnowszą wersję nowszą od lokalnej.
- Preferuje release APK.
- Pobiera `.sha256` i weryfikuje plik APK.
- Czytelniejsze komunikaty błędów i zgody Androida.

## 0.2.7

Cel: poprawki UX po testach użytkownika.

- Odchudzone ulubione komendy.
- Usunięty `EKRAN ON/OFF` z dolnego paska.
- Przełącznik niewygaszania ekranu w pasku statusu hosta.
- Klonowanie profili hostów.

## 0.2.6

- Widoczny przycisk `Usuń` dla ulubionych.
- Układ komend rozbity na wiersze.
- Przycisk ekranu przeniesiony z górnej belki.

## 0.2.5

- Hotfix zapisu profili i ulubionych przez `commit()`.
- Backup profili.
- Poprawki komunikatów hosta/DNS.

## 0.2.4

- Trwały zapis profili, ulubionych i ustawień terminala.
- Android Keystore dla sekretów.
- Keep screen awake.

## 0.2.3

- Kolorowy terminal ANSI.
- Poprawione `clear` i OSC prompt.
- Ctrl+D / exit zamyka ekran terminala po zakończeniu shell.

## 0.2.2 i wcześniejsze

- Pierwszy działający prototyp terminala.
- Profile SSH/Telnet.
- Fullscreen terminal.
- Ulubione komendy.
- CI i podpis APK dla aktualizacji.
