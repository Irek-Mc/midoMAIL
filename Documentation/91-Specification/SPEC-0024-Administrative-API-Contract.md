# SPEC-0024 — Administrative API Contract

**Status:** Accepted (uszczegółowiony w Iteracji 5.5 na podstawie ADR-0018..0021; decyzje 5-10 pozostają otwarte, dokument będzie aktualizowany po każdej)
**Powiązane dokumenty:** 91-Specification/SPEC-0002-Porty.md, 91-Specification/SPEC-0006-Adapter-Contract.md, 90-ADR/ADR-0002-UI-jako-klient-adaptera.md, 60-User-Interface/63-Routing.md, 60-User-Interface/64-Adaptery.md, 60-User-Interface/65-Konfiguracja.md

---

# Cel

`50-Quality/55-Roadmap.md` §7 (kryterium wyjścia Fazy 5) wymaga, żeby Adapter REST i Adapter CLI udostępniały „pełny zestaw operacji administracyjnych (odczyt stanu, zarządzanie adapterami, konfiguracja, routing) opisanych w SPEC-0006" — ale **SPEC-0006-Adapter-Contract.md tych operacji nie definiuje**: to generyczny kontrakt per-adapter (`AdapterId`, `AdapterVersion`, `SupportedChannels`, `SupportedCapabilities`, `HealthStatus`, `Metrics`, cykl życia wg SPEC-0014) wspólny dla Email/GSM/REST/CLI/WebSocket — nie ma w nim nic o operacjach administracyjnych.

Dodatkowo `20-Adapters/23-Adapter-REST.md` opisuje Adapter REST jako kanał transportowy HTTP↔GatewayMessage (zgodny z SPEC-0006/SPEC-0010 w dosłownym sensie), podczas gdy `20-Adapters/25-Adapter-CLI.md` wprost przypisuje mu „wykonywanie operacji administracyjnych" — dwa zaakceptowane dokumenty opisujące tę samą kategorię inaczej.

Rozstrzygające jest `SPEC-0002-Porty.md`, §Kategorie portów: **Administration Ports** są wymienione jako odrębna kategoria od **Plugin Extension Ports** (= `Adapter`, SPEC-0006/SPEC-0010) od samego początku projektu (Faza 0) — nigdy nie doczekały się własnej specyfikacji. Ten dokument ją dostarcza.

---

# Decyzja architektoniczna

**Adapter REST i Adapter CLI konsumują nowe, odrębne Administration Ports w `:domain` — nie implementują interfejsu `Adapter` (SPEC-0006/SPEC-0010).** Nie są rejestrowane w `Registry`, nie mają cyklu życia `AdapterState`, nie uczestniczą w `RoutingEngine`/`ExactlyOnceEngine`. Ramowanie transportowe z `23-Adapter-REST.md` (HTTP↔GatewayMessage) jest jawnie odłożone jako przyszłe, odrębne rozszerzenie — poza zakresem Fazy 5.

Ten sam duch co ADR-0016 (Notification Channel jako port odrębny od Adapter, Faza 4) — administracja, tak jak powiadomienia, nie jest transportem wiadomości biznesowych.

---

# Cztery kategorie operacji (źródłowane z dokumentów UI, nie wymyślone)

## 1. Odczyt stanu

Źródło: `64-Adaptery.md` §2-3, `63-Routing.md` §2, `65-Konfiguracja.md` §2, plus już istniejąca infrastruktura Fazy 4.

- Lista zarejestrowanych adapterów: nazwa/typ, wersja, stan (`AdapterState`), obsługiwane kanały, ostatnia aktywność, health — źródło danych: `Registry` (rozszerzony o enumerację, ADR-0018), `Adapter.health()`/`metrics()`.
- Szczegóły jednego adaptera: konfiguracja, obsługiwane możliwości (`Capability`), statystyki (`StatisticsAggregator`, Faza 4), ostatnie błędy, historia zdarzeń (`DiagnosticsFacade`/`EventStore`, Faza 4).
- Lista reguł routingu z pełnym modelem pola (`RuleId`/`Priority`/`Enabled`/`Conditions`/`TargetChannel`/`TargetAdapter`/`DeliveryPolicy`/`SetPriority`/`Version`) — źródło: nowy port z ADR-0021.
- Sekcje konfiguracji — źródło: `ConfigurationProvider` (rozszerzony, ADR-0020).

## 2. Zarządzanie adapterami

Źródło: `64-Adaptery.md` §4.

Operacje: Dodaj adapter, Edytuj konfigurację, Włącz/Wyłącz, Restart, Test połączenia, Usuń.

**Rozstrzygnięte w Iteracji 5.11:** „Dodaj adapter" (runtime, przez API) **poza zakresem tej fazy** — SPEC-0010 opisuje wyłącznie rejestrację sterowaną przez punkt kompozycji (`AdapterFactory`), nie dynamiczne tworzenie z API. `POST /adapters` istnieje i jawnie zwraca `501 Not Implemented` z czytelnym komunikatem — nie ciche 404. Nowy adapter wymaga zmiany konfiguracji punktu kompozycji i restartu procesu.

**Mapowanie pozostałych operacji na model cyklu życia (SPEC-0014):**
- Włącz/Wyłącz — `Registry.transitionTo(READY)`/`transitionTo(DEGRADED)` (`DEGRADED` jest jedynym stanem nieterminalnym dwukierunkowo powiązanym z `READY`; `STOPPED` jest terminalny).
- Restart — pełny cykl `stop()`→`unregister()`→`register()` na TEJ SAMEJ instancji adaptera (rzeczywiste ponowne wywołanie `start()`/`stop()`).
- Test połączenia — `Adapter.health()` na żądanie, bez efektów ubocznych.
- Usuń — `stop()`→`unregister()` + usunięcie z widoku żywych instancji.

**Odkrycie architektoniczne w trakcie:** `ReadStateEndpoints` (Iteracja 5.10) pierwotnie przyjmował `List<Adapter>` niemutowalną — niewystarczające, skoro operacje zarządzania (usuń/restart) muszą być NATYCHMIAST widoczne w odczycie. Wprowadzono `ManagedAdapters` — mały, mutowalny, współdzielony widok żywych instancji (ten sam duch co `AdapterRegistryOutbound` z Fazy 3) — `ReadStateEndpoints` zrefaktoryzowany, żeby go używać zamiast surowej listy.

## 3. Konfiguracja

Źródło: `65-Konfiguracja.md` §3.

Operacje: Walidacja przed zapisaniem, Test konfiguracji, Podgląd zmian, Import/Eksport (YAML), Historia zmian, Przywrócenie poprzedniej wersji. **Zakres tej fazy (decyzja z planu Fazy 5):** wąski zapis (`ConfigurationProvider.setValue`) + historia/rollback w pamięci (ADR-0020) — BEZ pełnego parsera plików YAML/importu-eksportu/walidacji krzyżowej (§Walidacja krzyżowa, SPEC-0005) — jawnie odnotowane jako dług architektoniczny, nie cicho pominięte.

## 4. Routing

Źródło: `63-Routing.md` §3-4.

Operacje: Dodaj/Edytuj regułę, Wyłącz/Włącz, Zmień priorytet, Ustaw/usuń `SetPriority`, Testuj regułę na przykładowym `GatewayMessage` (symulator — §4: bez wysyłania rzeczywistego komunikatu), Historia zmian. Źródło danych: nowy port z ADR-0021.

---

# Uwierzytelnianie i audyt (przekrojowe, dotyczy wszystkich czterech kategorii)

- Każda operacja administracyjna (odczyt i zapis) wymaga uwierzytelnienia — mechanizm rozstrzygnięty w ADR-0019 (statyczny klucz API przez `SecretStore`).
- Operacje zapisu (i błędy uwierzytelnienia) są audytowane jako `Event(category = EventCategory.ADMINISTRATIVE)` przez `EventPublisher`/`EventStore` (Faza 4, SPEC-0023 — bez zmian, `EventCategory.ADMINISTRATIVE` istniała od Fazy 1, nieużywana do tej pory). Zakres audytu odczytów — otwarty, rozstrzygany w Iteracji 5.16.

---

# Kształty żądanie/odpowiedź (Iteracja 5.5, na podstawie ADR-0018..0021)

Kanałowo-neutralne — te same pola niezależnie od tego, czy konsumuje je Adapter REST (JSON) czy CLI (tekst/tabela); dokładny format serializacji rozstrzyga ADR-0024 (Iteracja 5.8).

## Odczyt stanu

- **Lista adapterów** — dla każdego `AdapterId` z `Registry.registeredAdapterIds()`: `adapterId`, `state` (`Registry.stateOf`), `health` (`Adapter.health()`), `metrics` (`Adapter.metrics()`). Nazwa/typ/kanały pochodzą z `Adapter.supportedChannels()`/`supportedCapabilities()` — Registry sam ich nie przechowuje (zgodnie z ADR-0018, granica nienaruszona), więc odczyt wymaga żywej instancji `Adapter` po stronie punktu kompozycji (wzorem `AdapterRegistryOutbound`/`HealthMonitor`).
- **Szczegóły adaptera** — jak wyżej + `StatisticsAggregator.snapshots()` filtrowane po `adapterId`, `DiagnosticsFacade.messageTrace(correlationId)` dla śladu zdarzeń.
- **Lista reguł routingu** — `RoutingRuleAdministration.list()`, pełny model pola (`RuleId`/`Priority`/`Enabled`/`Conditions`/`TargetChannel`/`TargetAdapter`/`DeliveryPolicy`/`SetPriority`/`Version`).
- **Sekcje konfiguracji** — `ConfigurationProvider.getValue(key)`/`history(key)` dla ustalonego zbioru kluczy (kluczowa przestrzeń nazw pozostaje otwarta — dokładne klucze rozstrzygane wraz z Iteracją 5.12, gdy pojawi się konkretny konsument).

## Zarządzanie adapterami

- **Włącz/Wyłącz** — `Registry.transitionTo(id, READY|DEGRADED)` (istniejący, zamrożony kontrakt Fazy 1).
- **Restart** — `Registry.stop(id)` + ponowne `register(id, ...)` z tą samą, już istniejącą instancją adaptera.
- **Test połączenia** — wywołanie `Adapter.health()` na żądanie (bez efektów ubocznych, czysty odczyt).
- **Usuń** — `Registry.stop(id)` + `unregister(id)` (wymaga uprzedniego `STOPPED`, zgodnie z istniejącym kontraktem).
- **Dodaj adapter** — jeszcze nierozstrzygnięte (decyzja 9, Iteracja 5.11).

## Konfiguracja

- **Zapis** — `ConfigurationProvider.setValue(key, value)`.
- **Historia/Rollback** — `ConfigurationProvider.history(key)`/`rollback(key)`.
- **Walidacja przed zapisaniem, Test konfiguracji, Podgląd zmian, Import/Eksport YAML** — poza zakresem tej fazy (ADR-0020, §Świadomie POZA zakresem).

## Routing

- **Lista/Dodaj/Edytuj/Usuń** — `RoutingRuleAdministration.list()`/`add()`/`update()`/`remove()`.
- **Wyłącz/Włącz** — `update()` z `enabled` zmienionym w kopii reguły.
- **Zmień priorytet** — `update()` z `priority` zmienionym.
- **Ustaw/usuń SetPriority** — `update()` z `setPriority` zmienionym/wyzerowanym.
- **Symulator** (`63-Routing.md` §4) — wywołanie `RoutingRuleAdministration.buildEngine().route(przykladowaWiadomosc)`, bez `GatewayEngine`/bez efektów ubocznych. Rozstrzygane szczegółowo w Iteracji 5.13.

---

# Otwarte decyzje (zamykane w kolejnych iteracjach, ten dokument aktualizowany po każdej)

| # | Decyzja | Status |
|---|---|---|
| 1 | Enumeracja `Registry` | ✅ Rozstrzygnięte — ADR-0018, Iteracja 5.1 |
| 2 | Mechanizm uwierzytelniania | ✅ Rozstrzygnięte — ADR-0019, Iteracja 5.2 |
| 3 | Głębokość zapisu konfiguracji | ✅ Rozstrzygnięte — ADR-0020, Iteracja 5.3 |
| 4 | Administracja reguł routingu | ✅ Rozstrzygnięte — ADR-0021, Iteracja 5.4 |
| 5 | Moduły/platforma hostująca | ✅ Rozstrzygnięte — ADR-0022, Iteracja 5.6 |
| 6 | Serwer HTTP | ✅ Rozstrzygnięte — ADR-0023, Iteracja 5.7 |
| 7 | Biblioteka JSON | ✅ Rozstrzygnięte — ADR-0024, Iteracja 5.8 |
| 8 | Parsowanie argumentów CLI | ✅ Rozstrzygnięte — ADR-0025, Iteracja 5.9 |
| 9 | Zakres „Dodaj adapter" (runtime) | ✅ Rozstrzygnięte — poza zakresem, Iteracja 5.11 |
| 10 | Zakres audytu odczytów | ✅ Rozstrzygnięte — zapisy+błędy auth zawsze, odczyty nieaudytowane, Iteracja 5.16 |

**Wszystkie 10 decyzji rozstrzygnięte — dokument zamknięty (Iteracja 5.18).**

---

# Zasady zgodności

Kształty żądanie/odpowiedź powyżej są zamrożonym kontraktem — rozszerzenie wymaga nowego ADR, tak jak inne SPEC projektu.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 60-User-Interface/63-Routing.md
- 60-User-Interface/64-Adaptery.md
- 60-User-Interface/65-Konfiguracja.md
- 90-ADR/ADR-0002-UI-jako-klient-adaptera.md
- 90-ADR/ADR-0016-Notification-Channel-Port.md
- 91-Specification/SPEC-0002-Porty.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0023-Diagnostics-Event-Store-Contract.md
