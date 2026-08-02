# Client SSH 0.3.6 — telemetria VPS przez SSH

## Dwa niezależne poziomy monitoringu

### 1. TCP availability

Dotychczasowy Health Monitor pozostaje lekkim testem dostępności:

- telefon próbuje otworzyć połączenie TCP do `host:port` profilu,
- wynik mówi, czy wskazany port przyjął połączenie,
- podany czas to czas zestawienia TCP obserwowany przez telefon,
- test nie loguje się do SSH i nie odczytuje stanu systemu.

TCP `ONLINE` nie oznacza automatycznie, że uwierzytelnienie SSH, powłoka albo wszystkie usługi VPS działają poprawnie.

### 2. SSH telemetry

Opcjonalna telemetria otwiera krótkotrwały kanał SSH `exec` i uruchamia stały zestaw poleceń tylko do odczytu. Nie otwiera terminala interaktywnego i nie wymaga instalowania agenta na VPS.

Zbierane metryki:

- użycie CPU z dwóch próbek `/proc/stat`,
- RAM użyty i dostępny z `/proc/meminfo`,
- load average 1/5/15 z `/proc/loadavg`,
- zajętość głównego systemu plików przez `df -Pk /`,
- uptime z `/proc/uptime`,
- sumaryczny ruch RX/TX na sekundę z dwóch próbek `/proc/net/dev`,
- opcjonalny ICMP ping uruchamiany przez VPS do skonfigurowanego celu.

## Co oznacza ping

W aplikacji występują dwa różne pomiary opóźnienia:

1. **TCP latency** — telefon → port profilu. Pokazuje jakość trasy z telefonu i dostępność portu.
2. **ICMP ping** — VPS → wskazany cel, domyślnie `1.1.1.1`. Pokazuje łączność wychodzącą VPS i opóźnienie widziane przez serwer.

Brak narzędzia `ping` na VPS daje status `UNAVAILABLE`, ale nie unieważnia pozostałych metryk. Brak odpowiedzi ICMP daje `FAILED`. Ping można całkowicie wyłączyć.

## Wymagania

- profil typu SSH,
- uwierzytelnienie hasłem albo prywatnym kluczem,
- profil `INTERACTIVE` nie działa w tle,
- wcześniej zaufany klucz hosta w wspólnym pliku `known_hosts`,
- Linux z `/proc` i narzędziami POSIX używanymi przez skrypt,
- `ping` jest opcjonalny.

## Bezpieczeństwo

- telemetria jest domyślnie wyłączona,
- `StrictHostKeyChecking=yes`; nieznany albo zmieniony host key blokuje telemetrię,
- kanał `exec`, bez PTY i bez `sudo`,
- UI nie przekazuje dowolnych komend do collectora,
- cel ping akceptuje wyłącznie poprawny IPv4 albo nazwę DNS ASCII,
- limit odpowiedzi wynosi 16 KiB,
- obowiązuje twardy timeout połączenia i polecenia,
- surowy stdout/stderr nie jest przechowywany,
- historia zawiera tylko zwalidowane liczby i kontrolowane kody błędów,
- hasła, prywatne klucze i passphrase nie trafiają do raportów ani historii,
- tymczasowe bufory klucza i passphrase są zerowane po przekazaniu do JSch,
- usunięcie profilu usuwa również historię telemetrii.

## Harmonogram

WorkManager na Androidzie nie gwarantuje wykonania co do minuty. Minimalny interwał pozostaje 15 minut. Przycisk `Sprawdź teraz` wykonuje TCP oraz — jeśli włączona — telemetrię SSH natychmiast w zadaniu IO.

## Interpretacja statusów

- `SSH_OK` — pełna próbka, ewentualnie ping świadomie wyłączony,
- `SSH_PARTIAL` — zasoby odczytane, ale ping jest niedostępny albo nie odpowiedział,
- `HOST_KEY_NOT_TRUSTED` — host key nie został wcześniej zaufany,
- `AUTHENTICATION_FAILED` — odrzucone hasło/klucz/passphrase,
- `CONNECT_TIMEOUT` / `COMMAND_TIMEOUT` — przekroczony limit czasu,
- `COMMAND_FAILED` — system nie wykonał stałego skryptu,
- `RESPONSE_INVALID` — odpowiedź nie przeszła ścisłej walidacji.

Awaria telemetrii SSH nie zmienia stanu TCP. Port może pozostać `ONLINE`, a karta SSH jednocześnie pokazać błąd uwierzytelnienia lub host key.

## Test fizyczny przed wydaniem

1. Debian VPS z profilem hasłowym.
2. Debian VPS z prywatnym kluczem, również z passphrase.
3. Poprawna próbka CPU/RAM/load/dysk/uptime/RX/TX/ping.
4. Wyłączony ping.
5. Brak pakietu `ping`.
6. Błędne hasło lub passphrase.
7. Nieznany host key.
8. Zmieniony host key.
9. Timeout sieci i polecenia.
10. Restart aplikacji i odtworzenie ostatniej próbki.
11. Usunięcie profilu i potwierdzenie usunięcia całej historii.
