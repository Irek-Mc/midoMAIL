# ADR-0033 — Moduł `:ui-web` i mechanizm serwowania

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

Decyzja z Kontekstu planu Fazy 6 (potwierdzona przez AskUserQuestion): statyczny HTML/CSS/vanilla JS, serwowany przez lekki serwer HTTP, komunikacja przez `fetch()` z Admin REST API (Faza 5/6). Wymaga rozstrzygnięcia: (SA-12) jak przeglądarka się uwierzytelnia, (SA-13) jak przeglądarka dociera do `AdminHttpServer`, jeśli serwowana z innego portu/procesu.

## Decyzja

**Nowy moduł `:ui-web`** — wyłącznie zasoby statyczne (`HTML`/`CSS`/`JS` w `src/main/resources/static/`) + minimalny serwer plików statycznych (`com.sun.net.httpserver.HttpServer`, ten sam duch co `AdminHttpServer`, ale inna odpowiedzialność: serwowanie plików z classpath, nie routing API). **Zero zależności na `:domain`** — komunikacja z Adapterem REST wyłącznie przez `fetch()`/JSON po stronie klienta (przeglądarki), nie przez typy Kotlin po stronie serwera. To najsilniejsza możliwa granica UI/Core z całego projektu — silniejsza niż `:adapter-rest`/`:adapter-cli`, które przynajmniej zależą od typów domenowych bezpośrednio (ADR-0002 potwierdzone dosłownie: UI nie wywołuje portów Core, nawet pośrednio przez wspólny classpath).

**SA-12 (uwierzytelnianie przeglądarki):** ekran logowania w `:ui-web` przyjmuje klucz API, przechowuje go w `sessionStorage` (nie `localStorage` — czyszczony przy zamknięciu karty, mniejsze ryzyko pozostawienia klucza w przeglądarce współdzielonej), dołącza jako nagłówek `X-API-Key` do każdego `fetch()`. Odpowiedź 401 z dowolnego żądania przekierowuje z powrotem do ekranu logowania.

**SA-13 (CORS):** `AdminHttpServer` (Faza 5) dostaje amendment — nagłówek `Access-Control-Allow-Origin` na każdą odpowiedź (w tym błędy) + obsługa żądań `OPTIONS` (preflight) zwracająca 204 z nagłówkami `Access-Control-Allow-Methods`/`Access-Control-Allow-Headers`. **Ograniczone do `*` (wszystkie originy) — jawnie udokumentowane jako uproszczenie profilu deweloperskiego/jednego operatora**, nie produkcyjny model wieloserwisowy z listą dozwolonych originów. Uzasadnienie: ten sam klucz API chroni już każde żądanie zapisu/odczyt (ADR-0019) — CORS tutaj kontroluje wyłącznie, które strony WWW mogą inicjować żądanie z przeglądarki, nie zastępuje uwierzytelnienia.

## Konsekwencje

- `:ui-web` może być serwowany z innego portu niż `:adapter-rest` bez dodatkowej konfiguracji proxy — deweloperski model „dwa procesy na jednej maszynie" (ten sam duch co harnessy `manualVerification` z Fazy 5, ale tu produkcyjny kod, nie narzędzie weryfikacyjne).
- CORS `*` jest świadomym uproszczeniem — odnotowane w Architectural Debt Report Fazy 6 (Iteracja 6.30) jako punkt do zawężenia przy rzeczywistym wdrożeniu wieloserwisowym (Faza 7).
- Test kompilacji `:ui-web` potwierdza brak zależności na `:domain` (Architecture Gate) — silniejszy niż standardowa reguła „`:domain` zero zależności", bo tu CAŁY MODUŁ nie zależy na `:domain`.

## Dokumenty powiązane

- 60-User-Interface/60-UX-Filozofia.md
- 90-ADR/ADR-0002-UI-jako-klient-adaptera.md
- 90-ADR/ADR-0019-Uwierzytelnianie-API-Administracyjnego.md
- 90-ADR/ADR-0023-Serwer-HTTP.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
