# Client SSH — rotacja klucza release na Debianie

Ta procedura tworzy nowy klucz podpisujący Android poza repozytorium i zapisuje wymagane wartości jako GitHub Actions Secrets.

## Ważne

- Nie uruchamiaj generatora jako `root`.
- Nie commituj plików `.jks`, `.keystore`, `.b64` ani haseł.
- Zmiana certyfikatu wymaga odinstalowania aplikacji podpisanej starym kluczem przed instalacją pierwszego APK z nowym podpisem.
- Najważniejszy plik to `~/.client-ssh/signing/client-ssh-release.jks`; wykonaj co najmniej dwie zaszyfrowane kopie.

## 1. Przygotowanie

```bash
sudo apt update
sudo apt install -y git openjdk-17-jdk coreutils gh
```

Sprawdź narzędzia:

```bash
git --version
java -version
keytool -help >/dev/null
gh --version
```

## 2. Logowanie do GitHub CLI

```bash
gh auth login
```

W interaktywnym menu wybierz kolejno:

1. `GitHub.com`
2. `HTTPS`
3. `Yes` dla konfiguracji Git
4. `Login with a web browser`

Nie wpisuj następnych komend, dopóki menu logowania się nie zakończy i nie wróci zwykły prompt `$`.

Sprawdź:

```bash
gh auth status
gh repo view llipinsk82-rgb/client-ssh
```

## 3. Klon repozytorium

Polecenia Git muszą być wykonywane wewnątrz katalogu zawierającego `.git`.

```bash
gh repo clone llipinsk82-rgb/client-ssh ~/client-ssh-signing
cd ~/client-ssh-signing
git fetch origin
git switch agent/0.3.5-health-check-monitor
git pull --ff-only origin agent/0.3.5-health-check-monitor
git status
git branch --show-current
```

Oczekiwana gałąź:

```text
agent/0.3.5-health-check-monitor
```

## 4. Walidacja generatora

```bash
bash -n scripts/prepare-release-signing.sh
bash scripts/prepare-release-signing.sh --help
```

## 5. Generacja i zapis sekretów

```bash
bash scripts/prepare-release-signing.sh \
  --upload \
  --repo llipinsk82-rgb/client-ssh
```

Generator poprosi o dwa różne hasła, każde o długości minimum 16 znaków:

- hasło magazynu JKS,
- hasło prywatnego klucza.

Podczas wpisywania terminal nie pokazuje znaków ani gwiazdek. To prawidłowe.

Generator tworzy:

```text
~/.client-ssh/signing/client-ssh-release.jks
~/.client-ssh/signing/client-ssh-release.jks.b64
~/.client-ssh/signing/CERTIFICATE_SHA256.txt
```

oraz ustawia sekrety:

```text
CLIENT_SSH_RELEASE_KEYSTORE_B64
CLIENT_SSH_RELEASE_STORE_PASSWORD
CLIENT_SSH_RELEASE_KEY_ALIAS
CLIENT_SSH_RELEASE_KEY_PASSWORD
CLIENT_SSH_RELEASE_CERT_SHA256
```

Alias ma stałą wartość:

```text
client-ssh-release
```

## 6. Kontrola

```bash
gh secret list --repo llipinsk82-rgb/client-ssh
ls -la ~/.client-ssh/signing
tr -d '\n\r ' < ~/.client-ssh/signing/CERTIFICATE_SHA256.txt | wc -c
```

Ostatnie polecenie musi zwrócić `64`.

Sprawdź certyfikat:

```bash
keytool -list -v \
  -keystore ~/.client-ssh/signing/client-ssh-release.jks \
  -alias client-ssh-release
```

Fingerprint SHA-256 musi odpowiadać zawartości `CERTIFICATE_SHA256.txt`.

## 7. Kopia zapasowa

```bash
cd ~/.client-ssh
tar -czf - signing | gpg --symmetric --cipher-algo AES256 \
  --output client-ssh-signing-backup.tar.gz.gpg
```

Pobierz zaszyfrowane archiwum na prywatny komputer i wykonaj drugą kopię w innym bezpiecznym miejscu.

## 8. Jak CI używa klucza

Pull requesty nie otrzymują sekretów release i budują wyłącznie debug APK. Podpisany release jest budowany tylko przy pushu do `main` albo tagu `v*`.

Workflow:

1. sprawdza obecność pięciu sekretów,
2. dekoduje JKS tymczasowo do `app/signing/client-ssh-release.jks`,
3. buduje release APK,
4. weryfikuje fingerprint przez `apksigner`,
5. publikuje artefakt `client-ssh-release`,
6. usuwa tymczasowy JKS niezależnie od wyniku joba.

## 9. Migracja telefonu

Przed odinstalowaniem starej wersji zabezpiecz profile i klucze SSH. Następnie:

```bash
adb uninstall eu.blackserv.clientssh
adb install app-release.apk
```

Kolejne APK podpisane nowym kluczem można instalować jako aktualizację:

```bash
adb install -r kolejna-wersja.apk
```
