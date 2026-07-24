# Bezpieczne podpisywanie release APK

## Zasada

Klucz podpisujący, hasła, alias i zatwierdzony fingerprint certyfikatu nie mogą znajdować się w repozytorium ani w artefaktach buildów pull requestów.

Pull requesty uruchamiają wyłącznie:

- kontrolę braku materiału podpisującego w bieżącym drzewie,
- testy jednostkowe,
- debug APK podpisane standardowym kluczem debug Androida.

Podpisany release APK może zostać zbudowany wyłącznie przez zaufany push do `main` albo tag `v*` i tylko wtedy, gdy skonfigurowane są wszystkie wymagane GitHub Actions Secrets.

## Wymagane sekrety GitHub Actions

- `CLIENT_SSH_RELEASE_KEYSTORE_B64` — keystore zakodowany pojedynczą linią Base64,
- `CLIENT_SSH_RELEASE_STORE_PASSWORD` — hasło magazynu,
- `CLIENT_SSH_RELEASE_KEY_ALIAS` — alias klucza,
- `CLIENT_SSH_RELEASE_KEY_PASSWORD` — hasło klucza,
- `CLIENT_SSH_RELEASE_CERT_SHA256` — zatwierdzony SHA-256 certyfikatu release, jako 64 znaki hex; dwukropki i wielkość liter są ignorowane.

Przykład przygotowania wartości Base64 lokalnie:

```bash
base64 -w 0 client-ssh-release.jks
```

Na macOS:

```bash
base64 < client-ssh-release.jks | tr -d '\n'
```

Fingerprint certyfikatu należy odczytać lokalnie z docelowego keystore, a nie z niezweryfikowanego APK:

```bash
keytool -list -v \
  -keystore client-ssh-release.jks \
  -alias client-ssh \
  | grep 'SHA256:'
```

Wartość po `SHA256:` należy zapisać w `CLIENT_SSH_RELEASE_CERT_SHA256`. Przed ustawieniem sekretu fingerprint trzeba porównać drugim, niezależnym kanałem z zatwierdzonym certyfikatem projektu.

## Walidacja release

Workflow:

1. odtwarza keystore wyłącznie z sekretu,
2. nadaje mu uprawnienia `600`,
3. sprawdza alias przez `keytool`,
4. buduje release APK,
5. weryfikuje kryptograficzny podpis przez `apksigner`,
6. odczytuje SHA-256 certyfikatu z podpisanego APK,
7. porównuje go z `CLIENT_SSH_RELEASE_CERT_SHA256` i przerywa build przy jakiejkolwiek różnicy,
8. zapisuje `CERTIFICATE_SHA256.txt` oraz `SHA256SUMS.txt` obok APK,
9. usuwa tymczasowy keystore niezależnie od wyniku joba.

Samo przejście `apksigner verify` nie wystarcza. APK może być poprawnie podpisany niewłaściwym kluczem, dlatego release musi przejść również kontrolę oczekiwanego fingerprintu.

## Incydent starego klucza

Poprzedni materiał podpisujący został ujawniony w publicznej historii Git i musi być traktowany jako skompromitowany. Usunięcie go z bieżącego drzewa nie usuwa go ze starszych commitów.

Przed publicznym wydaniem należy:

1. ustalić strategię ciągłości aktualizacji istniejących instalacji,
2. wygenerować lub wybrać bezpieczny klucz docelowy,
3. zweryfikować i zatwierdzić fingerprint certyfikatu,
4. skonfigurować wszystkie sekrety,
5. przepisać historię repo przy użyciu `git filter-repo` albo BFG,
6. wymusić ponowne sklonowanie repo przez współpracowników,
7. opublikować fingerprint certyfikatu release.

Nie wolno rotować certyfikatu istniejącej aplikacji bez świadomej decyzji migracyjnej, ponieważ Android odrzuci aktualizację podpisaną innym kluczem.
