# Bezpieczne podpisywanie release APK

## Zasada

Klucz podpisujący, hasła i alias nie mogą znajdować się w repozytorium ani w artefaktach buildów pull requestów.

Pull requesty uruchamiają wyłącznie:

- testy jednostkowe,
- debug APK podpisane standardowym kluczem debug Androida.

Podpisany release APK może zostać zbudowany wyłącznie przez zaufany push do `main` albo tag `v*` i tylko wtedy, gdy skonfigurowane są wszystkie wymagane GitHub Actions Secrets.

## Wymagane sekrety GitHub Actions

- `CLIENT_SSH_RELEASE_KEYSTORE_B64` — keystore zakodowany pojedynczą linią Base64,
- `CLIENT_SSH_RELEASE_STORE_PASSWORD` — hasło magazynu,
- `CLIENT_SSH_RELEASE_KEY_ALIAS` — alias klucza,
- `CLIENT_SSH_RELEASE_KEY_PASSWORD` — hasło klucza.

Przykład przygotowania wartości Base64 lokalnie:

```bash
base64 -w 0 client-ssh-release.jks
```

Na macOS:

```bash
base64 < client-ssh-release.jks | tr -d '\n'
```

## Walidacja release

Workflow:

1. odtwarza keystore wyłącznie z sekretu,
2. nadaje mu uprawnienia `600`,
3. sprawdza alias przez `keytool`,
4. buduje release APK,
5. weryfikuje podpis przez `apksigner`,
6. generuje SHA-256,
7. usuwa tymczasowy keystore niezależnie od wyniku joba.

## Incydent starego klucza

Poprzedni materiał podpisujący został ujawniony w publicznej historii Git i musi być traktowany jako skompromitowany. Usunięcie go z bieżącego drzewa nie usuwa go ze starszych commitów.

Przed publicznym wydaniem należy:

1. ustalić strategię ciągłości aktualizacji istniejących instalacji,
2. wygenerować lub wybrać bezpieczny klucz docelowy,
3. skonfigurować sekrety,
4. przepisać historię repo przy użyciu `git filter-repo` albo BFG,
5. wymusić ponowne sklonowanie repo przez współpracowników,
6. opublikować fingerprint certyfikatu release.

Nie wolno rotować certyfikatu istniejącej aplikacji bez świadomej decyzji migracyjnej, ponieważ Android odrzuci aktualizację podpisaną innym kluczem.
