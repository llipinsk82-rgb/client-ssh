# Client SSH — aktualny handoff

Repozytorium: `llipinsk82-rgb/client-ssh`

Aktualnie rozwijana wersja: `0.3.13` (`versionCode 56`).

## Funkcje działające i potwierdzone na telefonie

- profile SSH i Telnet, logowanie hasłem oraz kluczem prywatnym,
- Terminal PTY z Session Keeperem, pełnym logiem i trwałym auto-zawijaniem,
- SFTP na VPS oraz Vu+ Zero / Enigma2,
- jawna weryfikacja fingerprintu i osobne klucze hosta dla każdego `host:port`,
- automatyczna zgodność z nowoczesnym SSH i starym Dropbear,
- Monitor TCP/SSH: CPU, RAM, load, dysk, uptime, sieć i ping,
- skiny Sapphire, Aurora i Obsidian oraz stały splash Sapphire,
- podpisane wydania i aktualizacje OTA,
- szyfrowany eksport/import `.bssh` (AES-256-GCM + PBKDF2), sprawdzony fizycznie.

## Etap 0.3.13

- przycisk `Testuj worker w tle` uruchamia prawdziwy, opóźniony pomiar WorkManager,
- opóźnienie 90 sekund pozwala zamknąć aplikację albo zrestartować telefon,
- worker po wykonaniu wysyła osobne powiadomienie testowe niezależnie od zmiany ONLINE/OFFLINE,
- wynik zapisuje się w istniejącej diagnostyce Monitora,
- celem jest domknięcie fizycznej walidacji Issue #20.

## Procedura testu 0.3.13

1. Włączyć monitoring i powiadomienia dla wybranego profilu.
2. Otworzyć `Opcje zaawansowane`.
3. Nacisnąć `Testuj worker w tle`.
4. Natychmiast zamknąć aplikację normalnie; nie używać `Wymuś zatrzymanie`.
5. Po około 90 sekundach oczekiwać powiadomienia `Test Monitora w tle zakończony`.
6. Powtórzyć test, restartując telefon zaraz po zleceniu zadania.
7. Po ponownym otwarciu aplikacji skopiować raport testowy i potwierdzić `SUCCESS` oraz `powiadomienie wysłane`.

## Bezpieczeństwo

- `StrictHostKeyChecking=yes` pozostaje obowiązkowe,
- fingerprinty nie są eksportowane w kopii konfiguracji,
- stały klucz release jest przechowywany wyłącznie w GitHub Actions Secrets,
- Issue #19 pozostaje otwarte: dawny ujawniony materiał podpisujący nadal istnieje w historii Git,
- przepisanie historii i force-push wymagają osobnej, jednoznacznej zgody użytkownika oraz lokalnego zaszyfrowanego backupu mirror.

## Następna kolejność

1. Fizyczny test WorkManager po zamknięciu aplikacji.
2. Fizyczny test WorkManager po restarcie telefonu.
3. Test pojedynczych alertów OFFLINE/ONLINE na profilu testowym.
4. Zamknięcie Issue #20 po udokumentowaniu wyników.
5. Osobne okno serwisowe do oczyszczenia historii Git według `docs/GIT_HISTORY_CLEANUP_PLAN.md`.
6. Następnie SFTP Pro, Terminal Pro i Monitor Pro.
