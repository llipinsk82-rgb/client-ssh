# Client SSH 0.3.6 — weryfikacja klucza hosta

## Cel

Pierwsze połączenie SSH nie ufa już automatycznie kluczowi przedstawionemu przez serwer.
Aplikacja blokuje sesję, pokazuje host, port, algorytm i fingerprint SHA-256 oraz czeka na jawną decyzję użytkownika.

## Pierwsze połączenie

1. Otwórz profil i wybierz połączenie.
2. Aplikacja wykona krótki handshake tylko po to, aby odebrać publiczny klucz hosta.
3. Pierwszy handshake zostanie zamknięty bez uwierzytelniania użytkownika.
4. Porównaj fingerprint z wartością uzyskaną niezależnym kanałem.
5. Dopiero po zgodnym porównaniu wybierz `Zaufaj po weryfikacji`.
6. Klucz zostanie zapisany w prywatnym `known_hosts`, a aplikacja rozpocznie nowe połączenie z `StrictHostKeyChecking=yes`.

## Niezależna weryfikacja na Debianie

Polecenie należy wykonać przez konsolę dostawcy VPS, lokalną konsolę, KVM albo istniejący zaufany kanał — nie przez nowe, jeszcze niezweryfikowane połączenie.

Dla ED25519:

```bash
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256
```

Dla ECDSA:

```bash
sudo ssh-keygen -lf /etc/ssh/ssh_host_ecdsa_key.pub -E sha256
```

Dla RSA:

```bash
sudo ssh-keygen -lf /etc/ssh/ssh_host_rsa_key.pub -E sha256
```

Porównaj pełny ciąg rozpoczynający się od `SHA256:` oraz algorytm pokazany przez aplikację.

## Zmieniony klucz

Jeżeli zapisany host przedstawia inny klucz:

- połączenie jest zawsze blokowane,
- aplikacja nie udostępnia przycisku automatycznego zastąpienia,
- nie usuwaj starego wpisu tylko po to, aby komunikat zniknął,
- sprawdź, czy VPS został przebudowany, klucze OpenSSH zostały świadomie obrócone, DNS wskazuje właściwy serwer i czy nie występuje atak MITM.

Planowana rotacja wymaga osobnej, kontrolowanej procedury usunięcia starego wpisu po niezależnym potwierdzeniu nowego fingerprintu.

## Migracja z wcześniejszych wersji

Wpisy utworzone wcześniej przez `accept-new` nie są uznawane za ręcznie zweryfikowane.
Przy pierwszym uruchomieniu nowej wersji:

- stary plik zostaje zachowany jako prywatna kopia audytowa `known_hosts.accept-new-unverified`,
- aktywny `known_hosts` zostaje wyczyszczony,
- każdy host wymaga jednorazowego jawnego potwierdzenia,
- kolejne uruchomienia zachowują już ręcznie zaakceptowane wpisy.

## Telemetria SSH

Terminal i telemetria korzystają z tego samego aktywnego `known_hosts`.
Telemetria nigdy nie pokazuje promptu w tle i nie akceptuje kluczy. Zacznie działać dopiero po zaakceptowaniu hosta w normalnym połączeniu terminalowym.

## Test telefonu

- [ ] pierwszy kontakt pokazuje prawidłowy host i port,
- [ ] algorytm i fingerprint odpowiadają wartości z konsoli VPS,
- [ ] `Anuluj` blokuje sesję,
- [ ] `Zaufaj po weryfikacji` zapisuje klucz i uruchamia świeże połączenie,
- [ ] ponowne połączenie i restart aplikacji nie pytają ponownie o ten sam klucz,
- [ ] niestandardowy port jest zapisywany jako osobna tożsamość hosta,
- [ ] zmieniony klucz pokazuje alarm bez opcji zaufania,
- [ ] telemetria działa dopiero po zaufaniu hostowi,
- [ ] rozłączenie podczas dialogu nie pozostawia oczekującej decyzji,
- [ ] stare wpisy `accept-new` wymagają ponownej weryfikacji.
