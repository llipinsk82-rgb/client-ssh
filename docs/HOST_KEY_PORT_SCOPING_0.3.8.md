# Client SSH 0.3.8 — rozdzielenie kluczy hosta według portu

- Każdy endpoint jest identyfikowany jako `[host]:port`.
- `blackserv.eu:22`, `blackserv.eu:3377` i `blackserv.eu:3388` mogą mieć różne klucze hosta bez fałszywego alarmu.
- Terminal, SFTP i telemetria korzystają z tej samej tożsamości endpointu.
- Przy pierwszym uruchomieniu 0.3.8 stare, niejednoznaczne wpisy host-only są archiwizowane i aktywny magazyn jest jednorazowo czyszczony.
- Każdy endpoint wymaga jednego ponownego porównania fingerprintu.
- Przy prawdziwej zmianie klucza użytkownik może usunąć wyłącznie stary wpis dla konkretnego hosta i portu; nowe połączenie nadal wymaga jawnego zaufania po weryfikacji.
- Nie dodano trybu `StrictHostKeyChecking=no` ani automatycznego akceptowania zmienionych kluczy.
