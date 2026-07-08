# ADR-0032 — Pełna konfiguracja YAML (import/eksport/walidacja krzyżowa)

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`60-User-Interface/65-Konfiguracja.md` §3 wymaga: „Walidacja przed zapisaniem... Import/Eksport (YAML)... Historia zmian... Przywrócenie poprzedniej wersji" — dokładnie zgodnie z `91-Specification/SPEC-0005-Configuration-Model.md` §Format pliku i §Walidacja krzyżowa (10 reguł krzyżowych, patrz SPEC-0005). ADR-0020 (Faza 5) świadomie odłożył to jako dług architektoniczny — teraz należny, bo Roadmapa §8 pkt 3 obejmuje ekran Konfiguracja.

## Decyzja

**Podział odpowiedzialności między `:domain` (bez zależności) i `:adapter-rest` (biblioteka YAML):**

1. **`:domain.configuration`** — typowany model `ConfigurationDocument` (odzwierciedlający dokładnie strukturę z SPEC-0005 §Przykładowy plik konfiguracyjny: `GatewayConfig`, `RoutingConfig`/`RoutingRuleConfig`, `AdapterConfigEntry`, `SchedulerConfig`, `SecurityConfig`, `MonitoringConfig`, `MessageStoreConfig`, `NotificationsConfig`) + `ConfigurationValidator` implementujący 10 reguł z §Walidacja krzyżowa. **Zero zależności zewnętrznych** — żadnej wiedzy o YAML, wyłącznie struktury danych i logika biznesowa walidacji.

2. **`:adapter-rest`** — `YamlConfigurationCodec` (serializacja `ConfigurationDocument`↔tekst YAML) przez **`kaml`** (biblioteka YAML oparta na `kotlinx.serialization`, już zaakceptowanym wyjątku z Fazy 5, ADR-0024) — **trzeci świadomy wyjątek od minimalizacji zależności**, naturalne rozszerzenie istniejącego frameworka serializacji (nie nowy, niepowiązany toolchain jak osobny parser refleksyjny — `kaml` używa tych samych adnotacji `@Serializable` co DTO z Fazy 5).

**`AdapterConfigEntry.config` jako `Map<String, String>`** (klucze typu `smtp.host`/`imap.folder`, ten sam duch co `AdapterConfigurationSchema` z Iteracji 6.6) — nie zagnieżdżone typy Kotlin per adapter, z tych samych powodów co ADR-0031 (unikanie zależności `:adapter-rest`→`:adapter-email`/`:adapter-gsm`).

**Zastosowanie zwalidowanego dokumentu** — cały dokument YAML jest przechowywany jako JEDNA wartość pod ustalonym kluczem `ConfigurationProvider` (`gateway.fullConfiguration`), reużywając istniejący mechanizm historii/rollback z Fazy 5 (ADR-0020) na poziomie CAŁEGO dokumentu, nie pojedynczych pól — „Przywrócenie poprzedniej wersji" (65-Konfiguracja.md §3) oznacza przywrócenie całego poprzedniego zrzutu konfiguracji, zgodnie z tym, jak plik YAML jest jednostką importu/eksportu w praktyce operacyjnej.

**Reguły krzyżowe zrealizowane w tej iteracji (10 z SPEC-0005):**
1. Adapter `email` wymaga `smtp.host`/`smtp.port`/`imap.host`/`imap.port`/`credentials.secretRef`.
2. Adapter `gsm` — `simSlot` (jeśli podany) musi być nieujemny; **dopasowanie do rzeczywiście dostępnego gniazda SIM raportowanego przez platformę pozostaje poza zakresem tego walidatora** (wymagałoby integracji z platformą uruchomieniową, której czysty walidator domenowy nie ma — udokumentowane ograniczenie, nie luka ukryta).
3. `routing.rules[].targetAdapter` musi odnosić się do istniejącego `adapters[].adapterId`.
4. Priorytet reguł NIE musi być unikalny — świadomy brak reguły (SPEC-0005 wprost to potwierdza).
5. `setPriority` musi być jedną z `LOW`/`NORMAL`/`HIGH`/`CRITICAL`.
6. `conditions` odwołuje się wyłącznie do `ChannelType` — **konstrukcyjnie wymuszone przez typ `RoutingRuleConditionsConfig`** (ma tylko `sourceChannel`/`destinationChannel`), nie wymaga walidacji w runtime.
7. `messageStore.deduplicationRetentionDays` ≥ `retentionDays`.
8. `adapters[].enabled == true` wymaga przejścia własnej walidacji sekcji `config` (łączy się z regułą 1/2).
9. `scheduler.tasks[].taskId` unikalny.
10. `security.secretStore` zgodny z platformą — walidator przyjmuje `platformProfile` jako parametr (np. `"android"`/`"jvm"`), nie odgaduje go sam.
11. `notifications.routing[].channels[]` odnosi się do istniejącego `notifications.channels[].channelId`.
12. `notifications.channels[].url`/`address` wymagane warunkowo wg `type`.

## Konsekwencje

- `:domain` pozostaje wolny od zależności zewnętrznych — `kaml` istnieje wyłącznie w `:adapter-rest`, tym samym duchem co `kotlinx.serialization` (ADR-0024).
- Reguła 2 (SIM slot) i część reguły 10 (pełna świadomość platformy) mają udokumentowane ograniczenie — `ConfigurationValidator` nie ma dostępu do rzeczywistego stanu sprzętu/platformy, wyłącznie do tego, co jest jawnie przekazane jako parametr.
- Historia/rollback na poziomie całego dokumentu (nie pojedynczych pól) — inny model niż `ConfigurationProvider.setValue` per klucz, ale spójny z tym, jak administratorzy faktycznie zarządzają plikami YAML (cały plik na raz, nie pole po polu).

## Dokumenty powiązane

- 60-User-Interface/65-Konfiguracja.md
- 90-ADR/ADR-0004-Format-Konfiguracji.md
- 90-ADR/ADR-0020-Konfiguracja-Zapis.md
- 90-ADR/ADR-0024-Biblioteka-JSON.md
- 90-ADR/ADR-0031-Adapter-Typed-Configuration.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
