# ADR-0007 — Dostarczanie powiadomień przez generyczny Webhook

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

00-Foundation/06-Glossary.md definiuje pojęcia „Alert" i „Powiadomienie", a 30-Infrastructure/35-Health-Monitor.md opisuje generowanie alertów — ale żaden dokument nie określał, w jaki sposób powiadomienie faktycznie dociera do administratora poza samym interfejsem Gateway (e-mail? push? integracja z PagerDuty/OpsGenie?), ani jak wygląda eskalacja, gdy alert pozostaje niepotwierdzony.

Zbudowanie osobnych, bespoke integracji z każdym systemem alertowania (PagerDuty, OpsGenie, Slack, Microsoft Teams) byłoby sprzeczne z zasadą minimalizacji zależności zewnętrznych (50-Quality/51-Standard-kodowania.md) i wymagałoby stałego utrzymania wielu integracji specyficznych dla dostawcy.

## Decyzja

Kanały dostarczania powiadomień:

- **Email** — przez już istniejący Adapter Email (20-Adapters/22-Adapter-Email.md), bez odrębnej implementacji SMTP.
- **Push** — możliwość platformowa (np. Android/FCM), zależna od platformy uruchomieniowej (40-Platforms/40-Android.md).
- **Webhook** — generyczne wywołanie HTTP POST pod skonfigurowany URL z ustrukturyzowanym ładunkiem (JSON) opisującym Alert.

**Integracja z PagerDuty, OpsGenie, Slack i podobnymi systemami odbywa się wyłącznie poprzez kanał Webhook** — każdy z tych systemów udostępnia własny, udokumentowany punkt przyjęcia webhooka. midoMAIL nie implementuje dedykowanego klienta dla żadnego z nich — administrator konfiguruje URL webhooka danego systemu jako zwykły kanał Webhook.

## Konsekwencje

- Brak dodatkowych zależności/bibliotek klienckich dla systemów alertowania — jeden generyczny mechanizm HTTP POST obsługuje wszystkie.
- Dodanie wsparcia dla nowego systemu alertowania w przyszłości (jeśli udostępnia webhook) nie wymaga zmian w Gateway — wyłącznie nowej konfiguracji kanału.
- Pełny model routingu Alert → kanał, konfiguracji kanałów oraz eskalacji jest zdefiniowany w 30-Infrastructure/38-Powiadomienia.md.
- Ryzyko: niektóre systemy oczekują specyficznego formatu ładunku webhooka (nie identycznego dla PagerDuty/OpsGenie/Slack) — adaptacja formatu ładunku do konkretnego odbiorcy jest odpowiedzialnością konfiguracji kanału (szablon ładunku), nie logiki biznesowej Gateway.

## Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 20-Adapters/22-Adapter-Email.md
- 30-Infrastructure/35-Health-Monitor.md
- 30-Infrastructure/38-Powiadomienia.md
- 50-Quality/51-Standard-kodowania.md
