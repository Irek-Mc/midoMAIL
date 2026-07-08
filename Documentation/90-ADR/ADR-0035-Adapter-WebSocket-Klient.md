# ADR-0035 — Adapter WebSocket jako klient, `java.net.http.WebSocket`, zero nowej zależności

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`50-Quality/55-Roadmap.md` §9 (Faza 7) wymaga adaptera WebSocket jako kolejnego transportu po REST/CLI. `20-Adapters/24-Adapter-WebSocket.md` nie rozstrzyga kierunku połączenia (serwer vs klient) — SPEC-0026 (Iteracja 7.0) analizuje to pytanie i wskazuje kierunek klienta jako zgodny z opisaną odpowiedzialnością („integracja z systemami wymagającymi dwukierunkowej komunikacji w czasie rzeczywistym", analogicznie do Adaptera Email będącego klientem SMTP/IMAP, nie serwerem pocztowym).

Ten ADR formalizuje decyzję i wybór technologiczny.

## Decyzja

**Adapter WebSocket łączy się wychodząco (klient) do skonfigurowanego adresu URL zewnętrznej usługi WebSocket.** Nie przyjmuje połączeń przychodzących — nie jest serwerem WebSocket.

**Implementacja: `java.net.http.WebSocket` (JDK 11+), zero nowej zależności zewnętrznej.** Projekt docelowy `:adapter-websocket` (`kotlin("jvm")`) potwierdzony jako kompilowany z JDK 11+ toolchain (ten sam, którego już używają `:adapter-email`/`:adapter-rest`) — API `java.net.http.WebSocket.Builder`/`Listener` jest częścią standardowej biblioteki, dostępne bez dodatkowego importu Gradle.

Odrzucone alternatywy:
- **Własna implementacja protokołu RFC 6455 na surowym `Socket`** — potrzebna wyłącznie, gdyby wymagany był tryb serwerowy (`com.sun.net.httpserver.HttpServer` nie wspiera WebSocket natywnie); niepotrzebny nakład pracy i ryzyko błędów protokołu (maskowanie ramek, fragmentacja, ping/pong) dla trybu klienckiego, gdzie JDK już dostarcza gotową, przetestowaną implementację.
- **Biblioteka zewnętrzna (np. Java-WebSocket, Ktor)** — czwarty świadomy wyjątek od minimalizacji zależności (po jakarta.mail, kotlinx.serialization, kaml) — odrzucone, bo JDK 11+ już rozwiązuje potrzebę bez żadnej nowej zależności.

## Konsekwencje

- `:adapter-websocket` zależy WYŁĄCZNIE od `:domain` (wzorem `:adapter-email`/`:adapter-gsm`) — zero nowych wpisów w `dependencies {}` poza `kotlin("jvm")` i standardowym `testImplementation(kotlin("test-junit5"))`.
- Tryb serwerowy pozostaje poza zakresem — jeśli przyszła faza wymaga przyjmowania połączeń WebSocket, potrzebny będzie nowy ADR i ponowna ocena zależności (nie cichy powrót do tej decyzji).
- Testy end-to-end (Iteracja 7.5) wymagają prawdziwego, lokalnie uruchomionego serwera WebSocket testowego (nie atrapy) — zgodnie z filozofią testowania projektu. Serwer testowy może być zbudowany na `com.sun.net.httpserver.HttpServer` z ręcznie zaimplementowanym minimalnym handshakiem RFC 6455 WYŁĄCZNIE w kodzie testowym (nie produkcyjnym) — akceptowalne, bo nie jest to Public API, wyłącznie narzędzie weryfikacyjne, analogicznie do `GreenMail` używanego w testach Adaptera Email.

## Dokumenty powiązane

- 20-Adapters/24-Adapter-WebSocket.md
- 91-Specification/SPEC-0026-WebSocket-Adapter-Contract.md
- 90-ADR/ADR-0024-Biblioteka-JSON.md (precedens: świadomy wyjątek od minimalizacji zależności, tu odwrotna decyzja — brak wyjątku)
