# ADR-0016 — Notification Channel jako port odrębny od Adapter

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

30-Infrastructure/38-Powiadomienia.md §2 definiuje rolę warstwy powiadomień jednoznacznie: „Odpowiada za dostarczenie treści Alertu... Nie generuje Alertów... Nie zawiera logiki biznesowej i **nie wpływa na przetwarzanie komunikatów**". To wprost odróżnia kanał powiadomienia od `Adapter` (SPEC-0010-Plugin-SDK-Contract.md), który jest zarejestrowany w `Registry`, uczestniczy w `RoutingEngine`, `ExactlyOnceEngine` i modelu `Channel`/`Capability` służącym do routingu wiadomości biznesowych.

Gdyby kanał powiadomień był `Adapter`-em, trzeba by wprowadzić syntetyczne `ChannelType` (np. `"push"`, `"webhook"`) do modelu routingu wyłącznie po to, żeby dostarczyć Alert — co zanieczyszczałoby `RoutingEngine`/`RoutingRule` pojęciem niezwiązanym z rzeczywistym przekazywaniem wiadomości między korespondentami (10-Core/13-Routing.md, §3: „Routing operuje wyłącznie na modelu GatewayMessage").

## Decyzja

`NotificationChannel` to nowy, odrębny port w `:domain` — **nie** rozszerzenie/implementacja interfejsu `Adapter`:

```kotlin
interface NotificationChannel {
    fun deliver(alert: Alert): NotificationResult
}
```

Konsekwencje tego rozróżnienia:
- Kanał powiadomień nie jest rejestrowany w `Registry`, nie ma cyklu życia `start()`/`stop()`/stanu `AdapterState` — jest tworzony i używany bezpośrednio przez `NotificationRouter` (30-Infrastructure/38-Powiadomienia.md §4).
- Kanał `Email` **wykorzystuje** już zarejestrowaną instancję `EmailAdapter` (przez `EmailNotificationChannel`, wołając `Adapter.send()` bezpośrednio) — ale sam nie jest tym adapterem ani go nie zastępuje. Wysyłka powiadomienia e-mail celowo pomija `GatewayEngine`/`RoutingEngine`/`ExactlyOnceEngine` (nie jest to wiadomość biznesowa podlegająca deduplikacji/routingowi wg reguł Gateway).
- Kanał `Webhook`/`Push` nie mają żadnego odpowiednika w modelu `Channel`/`ChannelType` — nie trzeba dodawać `ChannelType("webhook")`/`ChannelType("push")` nigdzie w `:domain`.

## Konsekwencje

- `RoutingEngine`/`RoutingRule`/`Channel` pozostają niezmienione — brak zanieczyszczenia modelu routingu pojęciami spoza przekazywania wiadomości.
- `NotificationChannel` nie wymaga żadnych zmian w `Registry`/`AdapterFactory`/`AdapterPorts` — całkowicie równoległy, mniejszy kontrakt.
- Kształt dokładnych metod/typów (`Alert`, `NotificationResult`) definiuje SPEC-0019-Notification-Channel-Contract.md.

## Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/13-Routing.md
- 20-Adapters (SPEC-0010-Plugin-SDK-Contract.md)
- 30-Infrastructure/38-Powiadomienia.md
- 91-Specification/SPEC-0019-Notification-Channel-Contract.md
