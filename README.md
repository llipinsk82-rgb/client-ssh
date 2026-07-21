# Client SSH

Nowy klient SSH, Telnet i SFTP dla Androida, projektowany pod codzienną administrację VPS-ami i tunerami Enigma2. Interfejs jest zoptymalizowany pod mały ekran Samsung Galaxy S20.

## Stan: 0.1.0 — fundament interfejsu

Pierwszy etap zawiera:

- profile SSH i Telnet z dowolnym portem,
- wybór hasła, klucza prywatnego lub logowania ręcznego,
- pełnoekranowy terminal z chowanymi belkami,
- przełączanie zawijania tekstu,
- klawisze Enter, Ctrl+C, Ctrl+D, Tab, strzałki, Esc, `clear` i `sudo -i`,
- edytowalne ulubione komendy,
- kopiowanie całego bufora,
- zapis logu przez systemowy wybór pliku,
- szkielety Health i SFTP,
- foreground service utrzymujący sesję po zablokowaniu telefonu,
- ikonę zgodną ze starym BlackServ SSH.

Warstwa sieciowa jest celowo kolejnym etapem. Obecny terminal jest prototypem UI i nie wykonuje jeszcze połączeń.

## Następne kroki

1. transport SSH z PTY, keepalive i prawidłowym zamknięciem kanału,
2. Telnet z negocjacją opcji i dowolnym portem,
3. Android Keystore oraz import klucza przez wklejenie lub plik,
4. trwałe profile i ulubione,
5. pełny log sesji zapisywany strumieniowo,
6. SFTP i Health na aktywnym połączeniu.

## Budowanie

Wymagane są JDK 17, Android SDK 37 i Gradle 9.5.0.

```bash
gradle :app:assembleDebug
```
