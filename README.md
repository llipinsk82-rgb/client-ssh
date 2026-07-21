# BlackServ Client SSH

Nowy klient SSH dla Androida przeznaczony do wygodnej obsługi prywatnych VPS-ów Linux oraz tunerów Enigma2.

Projekt jest budowany od podstaw. Główne założenia:

- prawdziwa interaktywna sesja SSH z PTY,
- profile VPS, Linux i Enigma2,
- klawisze ENTER, CTRL+C, CTRL+D, TAB, ESC i strzałki,
- skróty `sudo -i` oraz prawdziwy `clear`,
- bezpieczne wylogowanie i awaryjne rozłączenie,
- niezawodne kopiowanie oraz pełne logi sesji zapisywane do pliku,
- chowana belka i tryb pełnoekranowy terminala,
- sekrety przechowywane wyłącznie lokalnie przez Android Keystore.

## Status

Projekt w fazie inicjalizacji. Pierwszy etap obejmuje architekturę aplikacji, ekran profili oraz fundament terminala.

## Bezpieczeństwo

Nie umieszczaj w repozytorium haseł, prywatnych kluczy SSH, danych serwerów ani plików keystore.
