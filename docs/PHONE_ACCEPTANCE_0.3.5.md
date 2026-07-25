# Client SSH 0.3.5 — test odbiorczy Health Check Monitora

Celem testu jest potwierdzenie, że pomiary TCP wykonują się ręcznie i przez WorkManager po zamknięciu aplikacji oraz po restarcie telefonu. Test nie wymaga udostępniania hosta, loginu, hasła ani klucza.

## Warunki początkowe

- Zainstalowany debug APK z PR #21 (`versionName 0.3.5`, `versionCode 38`).
- Co najmniej jeden zapisany profil z osiągalnym hostem i portem TCP.
- Dla szybszego testu awarii przydatny jest drugi profil z zamkniętym lub nieosiągalnym portem.
- System nie może mieć wymuszonego zatrzymania aplikacji (`Force stop`). Android nie uruchamia WorkManagera dla aplikacji pozostającej w stanie force-stop.

## 1. Pomiar ręczny w aplikacji

1. Otwórz zakładkę **Monitor**.
2. Przy wybranym profilu naciśnij **Sprawdź teraz**.
3. Poczekaj na zmianę etykiety ze `Sprawdzanie…`.

### PASS

- UI nie zawiesza się.
- Pojawia się status `ONLINE` albo kolejny kontrolowany błąd.
- Aktualizują się: ostatni pomiar, latency lub licznik błędów oraz lista ostatnich pomiarów.
- Wielokrotne szybkie kliknięcie nie uruchamia równoległych pomiarów tego samego profilu.

## 2. Jednorazowy test prawdziwego workera

Po udostępnieniu akcji **Testuj worker teraz**:

1. Włącz monitoring profilem przełącznika.
2. Naciśnij **Testuj worker teraz**.
3. Obserwuj sekcję **Worker w tle**.
4. Po zakończeniu wybierz **Kopiuj raport testowy**.

### PASS

- Worker przechodzi przez `RUNNING` do `SUCCESS`, `SKIPPED` albo `RETRY`.
- Dla poprawnego, istniejącego i włączonego profilu oczekiwany wynik to `SUCCESS`.
- Raport zawiera hashowany `profile`, wynik workera, czasy i stan pomiaru.
- Raport nie zawiera hosta, loginu, hasła, klucza prywatnego ani pełnego identyfikatora profilu.

## 3. Naturalne wykonanie okresowe po zamknięciu aplikacji

1. Ustaw interwał **15 min** i włącz monitoring.
2. Zanotuj czas ostatniego pomiaru oraz ostatniego workera.
3. Zamknij aplikację z ekranu ostatnich aplikacji. Nie używaj `Force stop`.
4. Pozostaw telefon z aktywną siecią przez co najmniej 20–30 minut.
5. Otwórz aplikację i zakładkę **Monitor**.
6. Skopiuj raport testowy.

### PASS

- Czas ostatniego pomiaru jest późniejszy niż przed zamknięciem aplikacji.
- Sekcja **Worker w tle** pokazuje zakończone wykonanie.
- Raport zawiera `worker=SUCCESS` lub jednoznaczne `RETRY` z bezpiecznym komunikatem.

### FAIL

- Brak nowego pomiaru po 30 minutach przy dostępnej sieci.
- Worker pozostaje `RUNNING` przez długi czas bez aktualizacji.
- Aplikacja ulega awarii przy otwarciu zakładki Monitor.

Przy FAIL należy dodatkowo zanotować producenta telefonu, model, wersję Androida i ustawienia oszczędzania baterii.

## 4. Debounce OFFLINE i powiadomienia

1. Ustaw próg OFFLINE na `3 bł.`.
2. Użyj profilu z nieosiągalnym portem.
3. Wykonaj trzy kolejne pomiary.

### PASS

- Po pierwszym i drugim błędzie status nie przechodzi jeszcze do potwierdzonego `OFFLINE`.
- Po trzecim kolejnym błędzie status przechodzi do `OFFLINE`.
- Pojawia się dokładnie jedno powiadomienie o przejściu do OFFLINE.
- Kolejne błędy nie generują następnych identycznych alertów.

Następnie przywróć osiągalność hosta i wykonaj pomiar.

### PASS odzyskania

- Status wraca do `ONLINE`.
- Licznik błędów zostaje wyzerowany.
- Pojawia się dokładnie jedno powiadomienie o odzyskaniu dostępności.

## 5. Brak zgody na powiadomienia

Na Androidzie 13 lub nowszym odmów zgody `POST_NOTIFICATIONS`.

### PASS

- Pomiary ręczne i okresowe nadal działają.
- Historia i diagnostyka nadal są zapisywane.
- Brak zgody nie powoduje awarii workera.
- UI informuje, że wyłączone są alerty, a nie sam monitoring.

## 6. Restart telefonu

1. Pozostaw monitoring włączony.
2. Uruchom ponownie telefon.
3. Nie otwieraj aplikacji przez co najmniej 20–30 minut, zachowując dostęp do sieci.
4. Otwórz Monitor i skopiuj raport.

### PASS

- Konfiguracja monitoringu pozostała zachowana.
- WorkManager wykonał nowy pomiar po restarcie lub ma poprawnie zaplanowane zadanie oczekujące.
- Brak duplikatów zadań i serii powiadomień.

## Raport do zgłoszenia wyniku

Do komentarza w issue #20 wystarczy wkleić:

- model telefonu i wersję Androida,
- wynik sekcji 1–6: PASS/FAIL,
- skopiowany raport diagnostyczny,
- informację, czy aplikacja miała ograniczenia baterii.

Nie należy publikować zrzutów zawierających prawdziwy host, login, hasło lub klucz.
