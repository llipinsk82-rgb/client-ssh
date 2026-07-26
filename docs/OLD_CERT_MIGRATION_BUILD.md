# Lokalny build migracyjny podpisany starym certyfikatem

## Dlaczego jest potrzebny

APK podpisany nowym certyfikatem nie może zaktualizować aplikacji zainstalowanej ze starym certyfikatem. Jednocześnie odinstalowanie starej aplikacji usuwa profile i klucz Android Keystore.

Dlatego funkcja szyfrowanego eksportu musi zostać najpierw dostarczona jako jednorazowy build migracyjny podpisany starym certyfikatem.

## Model zaufania

Stary klucz podpisujący został ujawniony i jego podpis nie stanowi już samodzielnego dowodu autentyczności.

Build migracyjny jest uznawany za właściwy tylko wtedy, gdy jednocześnie:

- powstał z czystego, wskazanego commita Git,
- stary JKS znajduje się poza repozytorium i ma uprawnienia bez dostępu grupy/innych,
- certyfikat JKS odpowiada certyfikatowi APK faktycznie zainstalowanego na telefonie,
- gotowy APK ma oczekiwany package, `versionCode` i `versionName`,
- jego SHA-256 oraz commit źródłowy zostały zapisane lokalnie,
- artefakt nie został opublikowany w GitHub Releases, Actions, Issue ani publicznym hostingu.

## Przygotowanie OS2

Wymagane są:

- JDK 17 i `keytool`,
- Android SDK Build Tools 36 (`aapt`, `zipalign`, `apksigner`),
- Gradle 9.5,
- `adb`, gdy certyfikat ma być pobrany bezpośrednio z telefonu,
- lokalny stary JKS przechowywany poza repozytorium.

Stary JKS powinien mieć uprawnienia:

```bash
chmod 600 /bezpieczna/sciezka/stary.jks
```

## Budowanie

Przejdź na dokładny head PR #26 i upewnij się, że drzewo jest czyste:

```bash
git fetch origin
git switch agent/0.3.5-secure-profile-backup
git pull --ff-only origin agent/0.3.5-secure-profile-backup
git status --short
```

Przy telefonie podłączonym przez ADB uruchom:

```bash
bash scripts/build-old-cert-migration-apk.sh \
  --keystore /bezpieczna/sciezka/stary.jks \
  --alias STARY_ALIAS \
  --adb-reference
```

Skrypt interaktywnie poprosi o hasło magazynu i klucza. Nie przekazuj ich w argumentach, zmiennych środowiskowych, czacie ani Issue.

Alternatywnie można przekazać wcześniej pobrany bazowy APK:

```bash
bash scripts/build-old-cert-migration-apk.sh \
  --keystore /bezpieczna/sciezka/stary.jks \
  --alias STARY_ALIAS \
  --reference-apk /prywatna/sciezka/installed-old.apk
```

## Wynik

Domyślny katalog:

```text
~/.client-ssh/migration-build/
```

Zawiera:

- jednorazowy APK migracyjny,
- `SHA256SUMS.txt`,
- `CERTIFICATE_SHA256.txt`,
- `SOURCE_COMMIT.txt`,
- `README-MIGRATION.txt`.

Wszystkie pliki są tworzone z prywatnymi uprawnieniami. Sam APK nie zawiera JKS ani haseł, ale ponieważ jest podpisany ujawnionym certyfikatem, nie wolno traktować go jak publicznego release.

## Instalacja nad starą aplikacją

1. Nie odinstalowuj starej aplikacji.
2. Sprawdź SHA-256 lokalnego APK względem `SHA256SUMS.txt`.
3. Zainstaluj build jako aktualizację istniejącego pakietu, najlepiej kontrolowanym poleceniem:

```bash
adb install -r /sciezka/do/client-ssh-*-migration-old-cert-*.apk
```

4. Android powinien zachować dane aplikacji. Jeżeli instalacja zgłasza niezgodność podpisu, przerwij procedurę — nie używaj `-d`, nie odinstalowuj aplikacji i nie omijaj kontroli.
5. Otwórz `Ustawienia` → `Eksport / import profili` i utwórz zaszyfrowany backup.
6. Zachowaj co najmniej dwie kopie pliku i oddzielnie hasło.
7. Przeprowadź próbny import na kontrolowanym urządzeniu lub pustej instalacji.
8. Dopiero po pomyślnym teście można rozważyć odinstalowanie starej aplikacji i przejście na nowy certyfikat.

## Po migracji

- Build podpisany starym certyfikatem nie jest dalej rozwijany ani publikowany.
- Stary JKS nie trafia do repozytorium ani GitHub Actions.
- Normalne wydania używają wyłącznie nowego JKS z Actions Secrets.
- Lokalny APK migracyjny można usunąć dopiero po pełnym teście backupu i odzyskania wszystkich profili.
