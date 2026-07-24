# Client SSH 0.3.4 — test odbiorczy telefonu

Ten test należy wykonać na fizycznym urządzeniu przed oznaczeniem PR #17 jako gotowego do scalenia.

## Przygotowanie

1. Zainstaluj najnowszy APK 0.3.4 na istniejącej instalacji aplikacji.
2. Upewnij się, że działa co najmniej jeden profil SSH.
3. Włącz `Session Keeper` w ustawieniach.
4. Wyczyść zakończone wpisy Historii, aby wynik był jednoznaczny.

## A. Podstawowy zapis historii

1. Połącz się z profilem SSH.
2. Otwórz zakładkę `Historia`.

Oczekiwany wynik:

- dokładnie jeden wpis dla uruchomionej sesji,
- stan `AKTYWNA`,
- poprawny profil, użytkownik, host, port i protokół,
- brak hasła, klucza prywatnego i passphrase w interfejsie.

## B. Live refresh podczas rozłączenia

1. Pozostaw otwartą zakładkę `Historia`.
2. Rozłącz sesję z poziomu powiadomienia systemowego.

Oczekiwany wynik:

- bez opuszczania zakładki stan zmienia się z `AKTYWNA` na `ZAKOŃCZONA`,
- pojawia się czas trwania i komunikat rozłączenia,
- nie powstaje drugi wpis.

## C. Session Keeper i ubicie aplikacji

1. Połącz się ponownie.
2. Przejdź do ekranu ostatnich aplikacji i usuń Client SSH z listy.
3. Odczekaj co najmniej 10 sekund.
4. Wróć do aplikacji z powiadomienia `WRÓĆ`.
5. Otwórz Historię.

Oczekiwany wynik:

- terminal nadal działa albo automatycznie wraca przez reconnect,
- istnieje dokładnie jeden wpis tej sesji,
- wpis zachowuje pierwotny czas rozpoczęcia,
- reconnect nie tworzy nowego wpisu.

## D. Powrót do aktywnego terminala z Historii

1. Przy aktywnej sesji otwórz Historię.
2. Użyj akcji ponownego otwarcia aktywnego profilu.
3. W terminalu wykonaj prostą komendę, np. `echo test`.

Oczekiwany wynik:

- aplikacja wraca do istniejącego terminala,
- transport SSH nie jest resetowany,
- komenda wysyła się i zwraca wynik,
- Historia nadal zawiera jeden aktywny wpis.

## E. Ponowne połączenie zakończonej sesji

1. Rozłącz sesję.
2. W Historii wybierz `Połącz ponownie` dla zakończonego wpisu.

Oczekiwany wynik:

- aplikacja uruchamia nowe połączenie przy użyciu aktualnego profilu,
- powstaje nowy aktywny wpis,
- stary zakończony wpis pozostaje bez zmian.

## F. Usuwanie i czyszczenie

1. Usuń jeden zakończony wpis i potwierdź operację.
2. Uruchom sesję i użyj `Wyczyść historię` przy aktywnym wpisie.

Oczekiwany wynik:

- wskazany zakończony wpis znika,
- aktywnego wpisu nie można usunąć pojedynczo,
- czyszczenie usuwa wyłącznie zakończone wpisy,
- aktywna sesja pozostaje widoczna.

## Warunek akceptacji

PR #17 można oznaczyć jako gotowy do scalenia, gdy wszystkie sekcje A–F przejdą bez duplikatów wpisów, utraty aktywnego terminala i pozostawienia nieaktualnego stanu `AKTYWNA` po rozłączeniu.
