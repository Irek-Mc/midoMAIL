# SPEC-0025 — UI Client Contract

**Status:** Accepted (uszczegółowiony w Iteracji 6.8 — Część A planu Fazy 6 zamknięta, 7/7 luk rozstrzygniętych; Część B (rozszerzenie REST/CLI) w toku)
**Powiązane dokumenty:** 60-User-Interface/60 przez 71, 90-ADR/ADR-0002-UI-jako-klient-adaptera.md, 91-Specification/SPEC-0024-Administrative-API-Contract.md

---

# Cel

`50-Quality/55-Roadmap.md` §8 (kryterium wyjścia Fazy 6): „każda wartość prezentowana w UI pochodzi z jawnego kontraktu Core, żadna nie jest wyliczana ani rekonstruowana lokalnie przez UI." Zgodnie z ADR-0002, UI jest wyłącznie klientem Adaptera REST/Adaptera CLI (SPEC-0024) — ten dokument identyfikuje, co z 11 dokumentów `60-User-Interface` jest już pokryte przez SPEC-0024 (Faza 5) i istniejące porty `:domain`, a co wymaga nowych/rozszerzonych kontraktów.

---

# Co już jest pokryte (bez zmian w Core)

| Wymaganie UI | Źródło | Pokrycie |
|---|---|---|
| Lista/filtr/wyszukiwanie komunikatów, pełnotekstowe wyszukiwanie w treści | `62-Komunikaty.md` §3 | `MessageStore.query(MessageQueryFilter, MessageSort, PageRequest)` — `contentSearch`, `channelType`, `adapterId`, `processingState`, `messagePriority`, zakres czasu, `correlationId`, `externalReference` — wszystkie pola już istnieją |
| Wyszukiwanie po ExternalReference/MessageId, ślad komunikatu | `67-Diagnostyka.md` §4 | `MessageStore.findById`/`findByExternalReference`/`findByCorrelationId`, `DiagnosticsFacade.messageTrace` (Faza 4) |
| Potwierdzenie alertu zatrzymuje eskalację | `66-Monitoring.md` §4 | `EscalationScheduler.acknowledge(alertId)` (Faza 4) już usuwa alert ze śledzenia — brakuje wyłącznie portu admin do wywołania z zewnątrz (patrz ADR-0026) |
| Statystyki w zakresie czasu | `68-Statystyki.md` §4 | `StatisticsAggregator.snapshots()` zwraca migawki z `periodStart`/`periodEnd` — filtrowanie zakresu to warstwa Admin API, nie nowa zdolność Core |
| Reguły routingu (pola modelu, CRUD, symulator) | `63-Routing.md` | W pełni zbudowane w Fazie 5 (`RoutingRuleAdministration`, `RoutingEndpoints`) |
| Zarządzanie cyklem życia adaptera (enable/disable/restart/test/usuń) | `64-Adaptery.md` §4 | W pełni zbudowane w Fazie 5 (`AdapterManagementEndpoints`) |

---

# Luki wymagające nowych/rozszerzonych kontraktów (wszystkie rozstrzygnięte — Część A zamknięta)

## 1. Monitoring zasobów (CPU/RAM/Storage/Network) — ✅ ADR-0027 (Iteracja 6.2)

Źródło: `66-Monitoring.md` §2, `61-Dashboard.md` §2. `ResourceMonitor`/`ResourceSnapshot` (`:domain`), `AndroidResourceMonitor` (`:platform-android`), `UnavailableResourceMonitor` (stub JVM, wzorem `PushNotificationChannel`).

## 2. Ręczne ponowne przetworzenie komunikatu — ✅ ADR-0028 (Iteracja 6.3)

Źródło: `62-Komunikaty.md` §5, SPEC-0008 §Ręczne ponowne przetworzenie. `MessageStore.invalidateDeduplication` (amendment) + `MessageReprocessingAdministration` — przechodzi przez `GatewayInbound.receive()` normalnie, nie omija Exactly Once.

## 3. Administracja alertów — ✅ ADR-0026 (Iteracja 6.1)

Źródło: `66-Monitoring.md` §3-4, `71-Widoki-mobilne.md` §3. `EscalationScheduler.activeAlerts()` (amendment) + `acknowledge()` już istniejący od Fazy 4.

## 4. Historia zmian reguł routingu — ✅ ADR-0029 (Iteracja 6.4)

Źródło: `63-Routing.md` §3/§5. `RoutingRuleAdministration.history()` (amendment) — log chronologiczny ADDED/UPDATED/REMOVED. Pole „kto" czeka na RBAC (rozstrzygane w Iteracji 6.15, warstwa Admin API).

## 5. Pełny model RBAC (role, uprawnienia, konta) — ✅ ADR-0030 (Iteracja 6.5)

Źródło: `70-Uzytkownicy-i-uprawnienia.md`. `Role`/`Permission`/`Account`/`AccountStore`/`RoleStore`/`AccountAuthenticator` (`:domain.rbac`) — rola jako otwarty typ danych, hashowanie SHA-256 bez soli jako świadome uproszczenie tej fazy (patrz Architectural Debt Report, Iteracja 6.30).

## 6. Typowana konfiguracja adaptera — ✅ ADR-0031 (Iteracja 6.6)

Źródło: `64-Adaptery.md` §6. `AdapterConfigurationSchema`/`AdapterConfigurationAdministration` (`:domain`) — schemat tekstowy pól per typ adaptera (nie typy Kotlin z `:adapter-email`/`:adapter-gsm`, unikanie zależności międzymodułowej), zbudowany na `ConfigurationProvider` (Faza 5).

## 7. Pełny parser YAML / import-eksport / walidacja krzyżowa konfiguracji — ✅ ADR-0032 (Iteracja 6.7)

Źródło: `65-Konfiguracja.md` §3, SPEC-0005 §Walidacja krzyżowa i §Format pliku. `ConfigurationDocument`/`ConfigurationValidator` (`:domain`, zero zależności) + `YamlConfigurationCodec` (`:adapter-rest`, `kaml` — trzeci świadomy wyjątek od minimalizacji zależności). 10 reguł krzyżowych z SPEC-0005 zaimplementowane.

---

# Kontrakty gotowe do wystawienia przez Admin API (Część B, Iteracje 6.9-6.15)

| Kategoria | Port `:domain` | Iteracja Admin API |
|---|---|---|
| Alerty | `EscalationScheduler.activeAlerts()`/`acknowledge()` | 6.9 |
| Komunikaty (+ reprocess) | `MessageStore.query/findById/findByExternalReference/findByCorrelationId`, `MessageReprocessingAdministration` | 6.10 |
| Monitoring zasobów | `ResourceMonitor` | 6.11 |
| Historia routingu | `RoutingRuleAdministration.history()` | 6.12 |
| Konfiguracja adaptera | `AdapterConfigurationAdministration` | 6.13 |
| Konfiguracja YAML | `ConfigurationDocument`/`ConfigurationValidator`/`YamlConfigurationCodec` | 6.14 |
| RBAC | `Role`/`Account`/`AccountAuthenticator` | 6.15 |

---

# Luki znalezione podczas budowy ekranów (poza pierwotnym przeglądem z Iteracji 6.0)

## 8. Status Gateway, liczniki Exactly Once — ✅ ADR-0034 (Iteracja 6.18)

Znalezione podczas budowy Dashboardu (`61-Dashboard.md` §2): `HealthMonitor` nie miał odczytu bieżącego stanu zagregowanego, wersja/czas pracy procesu nie istniały jako kontrakt, `ExactlyOnceEngine` nie sumował `Accepted`/`Duplicate` w czasie. Rozstrzygnięte przez `AskUserQuestion`: `HealthMonitor.currentStatus()` (amendment), `GatewayInfo` (nowa struktura), `ExactlyOnceEngine.counters()` (amendment, wyłącznie `processed`/`duplicatesPrevented`).

## 9. Kolejki Schedulera — świadomie odłożone (ADR-0034, Iteracja 6.18)

`61-Dashboard.md` §2: „Model kolejek prezentuje stan zadań Schedulera" — `SchedulerProvider` (SPEC-0013) nie ma dziś żadnej introspekcji stanu zadań (wyłącznie `schedule`/`cancel`). Rozszerzenie portu o śledzenie Waiting/Processing/Retrying/Failed per zadanie to realny, nieprzewidziany nakład — jawnie odłożone, ekran Dashboard renderuje czytelny komunikat o niedostępności, nie fikcyjne dane.

## 10. Restart Gateway / Reload Configuration — świadomie niedostępne (SA-15, Iteracja 6.18)

`61-Dashboard.md` §3 wymienia operacje całego procesu — nieobecne w SPEC-0024/SPEC-0025 (które znają wyłącznie restart POJEDYNCZEGO adaptera). Ten sam wzorzec co „Dodaj adapter" (Faza 5) — UI jawnie komunikuje niedostępność (przyciski `disabled` z czytelnym `title`), nie ukrywa ani nie udaje wsparcia.

---

# Decyzje architektoniczne podjęte przed rozpoczęciem (potwierdzone przez AskUserQuestion)

1. **Technologia UI:** statyczny HTML/CSS/vanilla JS, serwowany jako pliki statyczne przez lekki serwer HTTP (`com.sun.net.httpserver.HttpServer`, ten sam duch co `AdminHttpServer`), komunikacja przez `fetch()` z Admin REST API. Zero nowego toolchainu (bez Node/npm).
2. **RBAC:** pełny model domenowy budowany i weryfikowany harnessem (analogicznie do Fazy 5 wobec `:adapter-rest`/`:adapter-cli`), bez rzeczywistego wdrożenia serwerowego. Android nadal używa uproszczonego modelu jednego administratora (`SecretStoreAdminAuthenticator`, Faza 5). Pełne wdrożenie produkcyjne dojrzewa w Fazie 7 razem z `:platform-jvm`.
3. **Widoki mobilne:** responsywny widok tej samej aplikacji `:ui-web` (media queries CSS) — zero nowego kodu natywnego Compose.

---

# Rejestr otwartych decyzji (SA-11..SA-15, rozstrzyganych w trakcie właściwej iteracji)

| # | Decyzja | Status |
|---|---|---|
| SA-11 | Strumień logów na żywo (69-Logi.md) — `com.sun.net.httpserver.HttpServer` bez natywnego SSE/WebSocket | Otwarte — Iteracja 6.21 |
| SA-12 | Uwierzytelnianie przeglądarki (klucz API → `sessionStorage`) | Otwarte — Iteracja 6.16 |
| SA-13 | CORS między `:ui-web` a `AdminHttpServer` | Otwarte — Iteracja 6.16 |
| SA-14 | Brak implementacji JVM dla `ResourceMonitor` (`:platform-jvm` dopiero Faza 7) | ✅ Rozstrzygnięte — `UnavailableResourceMonitor`, Iteracja 6.2 |
| SA-15 | Zakres „Restart Gateway"/„Reload Configuration" (61-Dashboard.md §3 — operacje całego procesu, nieobecne w SPEC-0024) | Otwarte — Iteracja 6.18 |

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 60-User-Interface/60-UX-Filozofia.md przez 71-Widoki-mobilne.md
- 90-ADR/ADR-0002-UI-jako-klient-adaptera.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0008-Exactly-Once-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
