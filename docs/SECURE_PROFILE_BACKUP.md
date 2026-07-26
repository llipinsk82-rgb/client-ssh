# Bezpieczny backup profili i migracja certyfikatu

## Cel

Nowy certyfikat podpisujący Client SSH nie jest zgodny ze starą instalacją. Android wymaga odinstalowania aplikacji podpisanej starym certyfikatem, co usuwa dane aplikacji oraz klucz Android Keystore używany do szyfrowania sekretów profili.

**Nie odinstalowuj starej aplikacji, dopóki zaszyfrowany backup nie zostanie utworzony i sprawdzony.**

## Co zawiera backup

Kontener obejmuje komplet profili, w tym:

- nazwy, hosty, porty i użytkowników,
- zapisane hasła SSH,
- prywatne klucze SSH,
- passphrase kluczy prywatnych,
- metodę uwierzytelniania i protokół.

Z tego powodu backup należy traktować jak prywatny klucz.

## Ochrona techniczna

Format `BSSHBK01`:

- nie używa plaintext JSON ani ZIP,
- wyprowadza 256-bitowy klucz przez PBKDF2-HMAC-SHA256 z losową solą,
- szyfruje AES-256-GCM z losowym nonce,
- uwierzytelnia nagłówek kontenera jako AAD,
- odrzuca zmieniony, obcięty, rozszerzony lub nieznany format,
- sprawdza ścisłe limity liczby profili i rozmiarów pól,
- nie zwraca częściowych danych po błędzie,
- ponownie szyfruje zaimportowane sekrety aktualnym kluczem Android Keystore.

## Zasady hasła backupu

- minimum 16 znaków,
- nie używaj hasła serwera, konta GitHub ani passphrase prywatnego klucza SSH,
- przechowuj hasło oddzielnie od pliku backupu,
- aplikacja nie zapisuje hasła backupu,
- utrata hasła oznacza brak możliwości odzyskania backupu.

## Procedura migracji

1. W starej instalacji otwórz `Ustawienia` → `Eksport / import profili`.
2. Wybierz `Utwórz zaszyfrowany backup`.
3. Ustaw nowe, unikalne hasło backupu.
4. Zapisz plik `client-ssh-profiles.bsshbackup` w prywatnej lokalizacji.
5. Utwórz co najmniej jedną dodatkową zaszyfrowaną kopię na innym nośniku.
6. Nie wysyłaj pliku ani hasła przez czat, e-mail, GitHub Issues lub publiczny załącznik.
7. Przed odinstalowaniem wykonaj próbny import na kontrolowanym urządzeniu albo instalacji testowej.
8. Dopiero po udanym teście odinstaluj aplikację podpisaną starym certyfikatem.
9. Zainstaluj APK podpisany nowym certyfikatem.
10. W pustej instalacji otwórz `Ustawienia` → `Eksport / import profili`.
11. Wybierz `Importuj backup do pustej instalacji` i podaj hasło.
12. Po automatycznym restarcie aplikacji sprawdź listę profili.
13. Wykonaj kontrolowane połączenie dla profilu hasłowego i profilu z prywatnym kluczem SSH.

## Ograniczenia bezpieczeństwa

- Import jest celowo dostępny tylko w instalacji bez profili.
- Aplikacja nie scala i nie nadpisuje automatycznie istniejących sekretów.
- Błędne hasło lub uszkodzony plik nie zmieniają bieżących profili.
- Systemowy Android Backup pozostaje wyłączony (`android:allowBackup="false"`).
- Release z nowym certyfikatem pozostaje zablokowany do czasu fizycznego testu pełnej migracji.

## Reakcja na problem

Jeżeli import się nie powiedzie:

1. nie usuwaj oryginalnego backupu,
2. nie edytuj pliku ręcznie,
3. nie wklejaj jego zawartości do Issue ani czatu,
4. zachowaj starą instalację i jej dane,
5. zgłoś jedynie wersję aplikacji, model Androida i niesekretny komunikat wyświetlony przez aplikację.
