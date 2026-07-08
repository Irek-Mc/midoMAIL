# ADR-0023 — Serwer HTTP dla Adaptera REST

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

Adapter REST (Iteracja 5.10+) potrzebuje serwera HTTP przyjmującego żądania administracyjne. ADR-0017 (Faza 4) wybrał `HttpURLConnection` dla WYCHODZĄCEGO klienta webhooka, uzasadniając to minimalizacją zależności (50-Quality/51-Standard-kodowania.md). `com.sun.net.httpserver.HttpServer` (wbudowany w JDK) był dotąd użyty wyłącznie jako atrapa testowa (`WebhookNotificationChannelTest`, Faza 4) — nigdy jako kod produkcyjny.

Rozważane opcje: (1) `com.sun.net.httpserver.HttpServer` — zero nowych zależności, już sprawdzony w testach projektu, ale minimalne API (brak wbudowanego routingu ścieżek, brak middleware, ręczne zarządzanie współbieżnością). (2) Framework (Ktor, Javalin, Spring) — wygodniejsze API, routing/middleware wbudowane, ale nowa, ciężka zależność sprzeczna z dotychczasową filozofią projektu.

## Decyzja

`com.sun.net.httpserver.HttpServer` jako produkcyjny serwer w `:adapter-rest` — kontynuacja filozofii „wbudowane w JDK" (ADR-0017), teraz po raz pierwszy jako serwer produkcyjny, nie tylko atrapa testowa.

Minimalny router zbudowany na bazie `HttpServer.createContext("/", ...)` — dyspozytor (metoda, ścieżka) → handler, z uwierzytelnieniem (`AdminAuthenticator`, ADR-0019) wymuszanym przed dyspozycją do KAŻDEGO zarejestrowanego handlera (401, jeśli klucz API nieprawidłowy/brak), 404 dla nierozpoznanej kombinacji metoda+ścieżka.

## Konsekwencje

- Zero nowych zależności Gradle.
- Brak wbudowanej obsługi TLS w `com.sun.net.httpserver.HttpServer` bez dodatkowej konfiguracji (`HttpsServer`/`SSLContext`) — poza zakresem tej iteracji; ryzyko odnotowane w raporcie końcowym Fazy 5 (Iteracja 5.18) jako świadomy dług (wdrożenie produkcyjne wymagałoby odwrotnego proxy z TLS lub jawnej konfiguracji `HttpsServer` w przyszłej iteracji).
- Współbieżność zarządzana przez domyślny executor `HttpServer` (pojedynczy wątek per żądanie, sekwencyjnie, chyba że jawnie skonfigurowany executor) — wystarczające dla administracyjnego API o niskim obciążeniu, nie dla ruchu produkcyjnego wysokiej częstotliwości.
- Ten sam serwer (jako klasa `AdminHttpServer`) jest testowany identycznym wzorcem co `WebhookNotificationChannelTest` z Fazy 4 — prawdziwy serwer, prawdziwy klient HTTP, nie atrapa frameworka.

## Dokumenty powiązane

- 90-ADR/ADR-0017-Webhook-Klient-HTTP.md
- 90-ADR/ADR-0019-Uwierzytelnianie-API-Administracyjnego.md
- 50-Quality/51-Standard-kodowania.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
