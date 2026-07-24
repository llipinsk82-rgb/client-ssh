# Health Check Monitor 0.3.5

## Cel

Monitor dostępności hostów działa niezależnie od terminala i nie przechowuje haseł, kluczy ani danych sesji.

## Rdzeń domenowy

- `HealthCheckStateMachine` odpowiada za przejścia `UNKNOWN / ONLINE / OFFLINE`.
- `HealthCheckRepository` wykonuje atomowe operacje read-modify-write i zachowuje stan po odtworzeniu procesu.
- `HealthProbe` oddziela transport od logiki domenowej.
- `TcpHealthProbe` wykonuje TCP connect z timeoutem i zawsze zamyka socket.

## Reguły stanu

- pierwszy sukces ustawia `ONLINE` bez powiadomienia,
- przejście do `OFFLINE` wymaga osiągnięcia progu kolejnych błędów,
- kolejne błędy w stanie `OFFLINE` nie generują nowych powiadomień,
- pierwszy sukces po `OFFLINE` przywraca `ONLINE` i generuje pojedyncze zdarzenie,
- pomiar starszy niż `lastCheckedAt` jest ignorowany,
- licznik błędów zatrzymuje się na `Int.MAX_VALUE`.

## Trwałość i współbieżność

Repozytorium używa blokady współdzielonej przez wszystkie instancje w procesie. Zapobiega to utracie profili, gdy Activity i WorkManager wykonują równoległe operacje na tym samym storage.

Format snapshotów ma wersję `v1`. Uszkodzone rekordy są pomijane bez utraty poprawnych wpisów.

## Następna kolejność

1. adapter Android SharedPreferences,
2. konfiguracja monitoringu per profil,
3. unikalny scheduler WorkManager per profil,
4. powiadomienia zmian stanu,
5. ekran Health Monitor,
6. test telefonu.
