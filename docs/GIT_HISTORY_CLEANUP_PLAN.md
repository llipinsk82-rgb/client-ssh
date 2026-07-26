# Client SSH — plan oczyszczenia historii Git

Ten dokument opisuje bezpieczną procedurę usunięcia ujawnionego materiału podpisującego ze wszystkich osiągalnych commitów repozytorium.

## Status

- nowy JKS release został wygenerowany lokalnie poza repozytorium,
- pięć wymaganych GitHub Actions Secrets zostało ustawionych,
- podpisany APK został zbudowany i zweryfikowany w pipeline #456,
- bieżące drzewo nie zawiera starego JKS ani jego Base64,
- stary materiał nadal istnieje w publicznej historii Git,
- ten plan nie wykonuje force-pusha ani przepisywania historii.

## Materiał objęty oczyszczeniem

Co najmniej:

- `.github/signing/client-ssh-update.jks.b64`,
- dawne jawne dane podpisujące w konfiguracji Gradle i workflow,
- wszelkie inne kopie starego keystore, Base64, haseł lub prywatnego materiału podpisującego wykryte podczas analizy wszystkich refów.

Dokument ani skrypty repozytorium nie mogą zawierać dawnych wartości haseł lub zawartości Base64. Lista zamian dla `git filter-repo` musi powstać lokalnie i pozostać poza repozytorium.

## Warunki wstępne

Przed jakąkolwiek zmianą historii muszą być spełnione wszystkie warunki:

1. Istnieją co najmniej dwie zaszyfrowane kopie nowego `client-ssh-release.jks` w dwóch niezależnych lokalizacjach.
2. Hasła nowego JKS są zapisane w menedżerze haseł.
3. Zakończono lub świadomie zamrożono pracę na otwartych PR #17 i #21.
4. Nie trwa publikacja release, tagowanie ani inna operacja zapisu do repozytorium.
5. Użytkownik wydał osobne, jednoznaczne polecenie wykonania przepisywania historii i force-pusha.

## Backup przed operacją

Backup zawiera ujawniony materiał, dlatego nie może trafić do GitHub, chmury bez szyfrowania ani publicznego załącznika.

Na zaufanej maszynie:

```bash
mkdir -p ~/client-ssh-history-cleanup
cd ~/client-ssh-history-cleanup

git clone --mirror git@github.com:llipinsk82-rgb/client-ssh.git client-ssh-before-cleanup.git
cd client-ssh-before-cleanup.git

git show-ref > ../refs-before-cleanup.txt
git bundle create ../client-ssh-before-cleanup.bundle --all
cd ..

gpg --symmetric --cipher-algo AES256 \
  --output client-ssh-before-cleanup.bundle.gpg \
  client-ssh-before-cleanup.bundle

rm -f client-ssh-before-cleanup.bundle
```

Należy sprawdzić możliwość odszyfrowania i odczytu bundla przed rozpoczęciem filtrowania.

## Analiza przed filtrowaniem

Pracować wyłącznie na kopii mirror, nigdy na zwykłym katalogu roboczym zawierającym niezacommitowane zmiany.

```bash
cd ~/client-ssh-history-cleanup/client-ssh-before-cleanup.git

git fsck --full
git filter-repo --analyze
```

Następnie należy sprawdzić wszystkie refy pod kątem:

- nazw keystore i plików Base64,
- bloków kluczy prywatnych,
- dawnych jawnych pól `storePassword` i `keyPassword`,
- innych kopii materiału podpisującego.

Nie kopiować wyników zawierających sekrety do issue, PR ani czatu.

## Filtrowanie

Minimalny zakres dla ujawnionego pliku:

```bash
git filter-repo \
  --path .github/signing/client-ssh-update.jks.b64 \
  --invert-paths
```

Dawne wartości tekstowe należy usunąć osobnym, lokalnym plikiem zamian przekazanym przez `--replace-text`. Plik ten musi:

- pozostać poza repozytorium,
- mieć uprawnienia `600`,
- zostać usunięty po walidacji,
- nigdy nie być wklejany do czatu ani komentarzy GitHub.

W razie wykrycia dodatkowych kopii binarnych należy powtórzyć filtrowanie z pełną, zatwierdzoną listą ścieżek.

## Walidacja po filtrowaniu

Przed jakimkolwiek push należy potwierdzić:

```bash
git fsck --full

git log --all -- .github/signing/client-ssh-update.jks.b64

git rev-list --objects --all | \
  grep -Eis '(^|/)([^/]+\.(jks|keystore|p12|pfx|pem|key)|.*signing.*\.b64)$' \
  && exit 1 || true
```

Dodatkowo:

1. liczba oczekiwanych branchy i tagów odpowiada zapisowi `refs-before-cleanup.txt`,
2. `main`, gałąź PR #17 i gałąź PR #21 budują się po przepisaniu,
3. `scripts/check-no-release-secrets.sh` przechodzi na każdej gałęzi, która ma zostać zachowana,
4. nowy JKS i pięć GitHub Actions Secrets nie są modyfikowane,
5. żaden plik backupu ani lista zamian nie znajduje się w drzewie Git.

## Publikacja przepisanej historii

Ten etap jest destrukcyjny i wymaga osobnej zgody użytkownika.

Przed force-pushem:

- wstrzymać wszystkie zapisy do repozytorium,
- zapisać końcową mapę starych i nowych refów,
- potwierdzić gotowość rollbacku,
- poinformować, że wszystkie stare klony muszą zostać usunięte lub ponownie sklonowane.

Dopiero po zatwierdzeniu:

```bash
git push --force --mirror origin
```

Nie wykonywać częściowego force-pusha bez wcześniej zatwierdzonej mapy branchy i tagów.

## Rollback

Jeśli walidacja GitHub po pushu wykaże problem:

1. natychmiast zamrozić kolejne zapisy,
2. odszyfrować backup bundle lub użyć zachowanego mirror clone,
3. przywrócić pełny zestaw refów z backupu,
4. ponownie zweryfikować `main`, PR-y, tagi i Actions,
5. udokumentować przyczynę niepowodzenia przed kolejną próbą.

Przykładowe odtworzenie do nowego katalogu:

```bash
mkdir client-ssh-rollback.git
cd client-ssh-rollback.git
git init --bare
git fetch ../client-ssh-before-cleanup.bundle 'refs/*:refs/*'
```

## Skutki dla współpracowników i PR-ów

Po przepisaniu historii:

- stare SHA przestaną być aktualnymi identyfikatorami commitów,
- otwarte PR-y mogą wymagać odtworzenia lub ponownego ustawienia gałęzi,
- wszystkie lokalne klony powinny zostać ponownie sklonowane,
- stary klon nie może zostać wypchnięty ponownie, ponieważ może przywrócić usunięty materiał,
- publicznych forków i wcześniej pobranych kopii nie można technicznie wycofać.

## Kryterium zakończenia issue #19

Issue #19 można zamknąć dopiero, gdy:

- nowy JKS ma potwierdzone zaszyfrowane backupy,
- podpisany release został zweryfikowany,
- historia wszystkich kontrolowanych refów została oczyszczona,
- GitHub nie pokazuje starego pliku w osiągalnych commitach,
- wszystkie aktywne gałęzie przechodzą CI,
- procedura migracji telefonu jest potwierdzona,
- nie ma ryzyka ponownego wypchnięcia starej historii.
