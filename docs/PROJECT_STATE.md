# BlackServ Client SSH — stan projektu

Ten dokument jest aktualizowany po wydaniach, żeby kolejny czat mógł kontynuować pracę bez ładowania całej historii rozmowy.

## Repozytorium

- GitHub: `llipinsk82-rgb/client-ssh`
- Aplikacja: Android / Kotlin / Jetpack Compose
- Pakiet: `eu.blackserv.clientssh`
- Obecna wersja robocza po tym wydaniu: `0.3.1`
- Podpis APK: stały keystore update, używany dla debug/release, żeby kolejne APK instalowały się jako aktualizacja.

## Cel aplikacji

Client SSH nie ma być zwykłym klientem SSH. To narzędzie administracyjne BlackServ do codziennej obsługi VPS-ów i tunerów Enigma2 z telefonu, szczególnie z małego ekranu Samsung Galaxy S20.

## Założenia funkcjonalne

### Profile hostów

- SSH i Telnet jako typy profilu.
- Port konfigurowany per profil.
- Logowanie hasłem.
- Logowanie kluczem prywatnym przez wklejenie lub wybór pliku.
- Obsługa formatów OpenSSH, PEM, PKCS#8 i PuTTY PPK.
- Sekrety zapisywane przez Android Keystore.
- Trwały zapis profili po restarcie i aktualizacji.
- Klonowanie profilu do szybkiej zmiany portu/nazwy.

### Terminal

- Prawdziwa sesja SSH z PTY.
- Terminal fullscreen.
- Foreground service i keepalive.
- Sesja powinna trwać przy blokadzie ekranu i przełączaniu aplikacji.
- `Ctrl+D` oraz `exit` zamykają ekran terminala po zakończeniu powłoki.
- `clear` wykonuje prawdziwe czyszczenie terminala.
- Lokalny `BUF CLEAR` czyści tylko bufor aplikacji.
- Kolorowy terminal z podstawową obsługą ANSI.
- Kopiowanie bufora i zapis logu.
- Ulubione komendy są edytowalne, przesuwalne i usuwalne.
- Terminal ma pinch-to-zoom rozmiaru czcionki. Na razie ustawienie zoomu jest lokalne dla ekranu, nie zapisuje się trwale.

### OTA

- Aktualizacje przez GitHub Releases.
- Aplikacja pobiera listę wydań i wybiera najnowsze wydanie nowsze niż lokalne.
- Preferuje podpisany release APK.
- Pobiera `.sha256` i weryfikuje APK przed instalacją.
- Androidowy instalator uruchamiany jest po pobraniu i sprawdzeniu pliku.

### SFTP

- Realny SFTP przez JSch / `ChannelSftp`.
- Używa tego samego profilu, hasła lub klucza co terminal SSH.
- Pokazuje aktualny katalog.
- Obsługuje wejście w katalog, przejście wyżej i odświeżenie.
- Pobiera pliki przez systemowy wybór miejsca zapisu Androida.
- Wysyła pliki przez systemowy picker Androida.
- Tworzy katalogi.
- Zmienia nazwy plików/katalogów.
- Usuwa pliki i puste katalogi po potwierdzeniu.
- Od wersji 0.3.0 widok SFTP jest kompaktowy w stylu Total Commander: cienkie wiersze, menu akcji pod trzema kropkami, mniej dużych kart.
- Od wersji 0.3.1 SFTP ma mocniejszy styl premium: warstwy, cienie, cienkie linie, obramowania i boczny akcent folder/plik.

## Wygląd / UX

Aktualny kierunek wizualny:

- ciemny BlackServ graphite,
- zielony terminalowy akcent,
- subtelny amber jako kolor drugorzędny,
- bez jaskrawego niebieskiego Material,
- mniej dużych okrągłych przycisków,
- bardziej profesjonalne, zwarte panele,
- nie tylko zmiana koloru: mają być widoczne warstwy, cienie, cienkie linie, separatory i obramowania,
- styl ma iść w stronę narzędzia admina premium, a nie zwykłego prototypu Material.

Wersja 0.3.0 zmieniła paletę, ekran główny i kompakt SFTP. Po opinii użytkownika uznano, że to było za mało — bardziej zmiana kolorów niż pełny design. Wersja 0.3.1 robi drugi pass: głębia paneli, cienie, boczne akcenty, techniczne przyciski, separatory i lepsze warstwowanie.

## Ostatnie wydania

### 0.3.1

- Premium UI pass po uwadze, że 0.3.0 zmieniło głównie kolory.
- Hosty: panele z cienką linią highlight, cieniem, bocznym akcentem i technicznymi przyciskami.
- SFTP: większa głębia, obramowania, akcent folder/plik, panelowy header.
- Globalne shapes i paleta dopasowane do stylu BlackServ.

### 0.3.0

- Nowy wygląd BlackServ poza terminalem.
- Wersja aplikacji widoczna na ekranie głównym.
- Kompaktowy SFTP w stylu Total Commander.
- Menu akcji dla pozycji SFTP.
- Pinch-to-zoom terminala.
- Dodana dokumentacja ciągłości projektu.

### 0.2.9

- Realny SFTP.
- Lista plików/katalogów.
- Upload/download/rename/delete/mkdir.

### 0.2.8

- Utwardzony OTA updater.
- Wybór najnowszego wydania z listy Releases.
- Weryfikacja SHA-256 pobranego APK.

### 0.2.7

- Odchudzone ulubione komendy.
- Przełącznik niewygaszania ekranu przeniesiony do paska statusu hosta.
- Dodane klonowanie profili.

## Najbliższa kolejność

1. Realny Health Monitor.
2. Dalsze poprawki SFTP po testach na telefonie.
3. Utrwalenie preferencji terminala, np. rozmiaru czcionki po pinch-to-zoom.
4. Dalsze dopracowanie wyglądu, jeżeli po testach użytkownik wskaże elementy do poprawy.

## Health Monitor — oczekiwany kierunek

Monitor ma być praktyczny, nie dekoracyjny. Powinien pokazać szybki stan hosta:

- ping / opóźnienie,
- uptime,
- load average,
- CPU,
- RAM,
- dysk,
- podstawowe informacje o systemie,
- dla Enigma2 docelowo dodatkowo stan usługi/tunera, gdzie to możliwe.

Najprostszy start: wykonywać zestaw komend po SSH i parsować wynik. Ważne, żeby nie zrywać głównej sesji terminala i nie blokować UI.

## Ważne uwagi testowe

- Usuwanie w SFTP działa realnie na serwerze. Testować ostrożnie.
- OTA najlepiej testować przechodząc z wersji poprzedniej na nowszą przez aplikację, nie przez ręczną instalację APK.
- Jeśli Android pyta o zgodę na instalowanie z aplikacji, trzeba ją włączyć i ponowić instalację.
- Profile po 0.2.5+ powinny zostawać po update.
