# ADR-0004 — YAML jako format konfiguracji

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

SPEC-0005-Configuration-Model.md definiował strukturę logiczną konfiguracji (sekcje Gateway/Routing/Adapters/Scheduler/Security/Monitoring), ale nie rozstrzygał, w jakim formacie plikowym konfiguracja jest zapisywana, importowana i eksportowana (60-User-Interface/65-Konfiguracja.md, §Funkcje). Bez tej decyzji UI „Konfiguracja" oraz implementacja Message Store/Adapter Factory (SPEC-0010-Plugin-SDK-Contract.md) nie miałyby wspólnego, konkretnego formatu do wczytania i zapisania.

Rozważane opcje: JSON, YAML, TOML.

- JSON nie wspiera komentarzy — plik konfiguracyjny wymagający wyjaśnień (np. dlaczego dany port SMTP, co robi `deliveryPolicy`) musiałby polegać na osobnej dokumentacji, rozjeżdżającej się z rzeczywistym plikiem.
- TOML jest prostszy i mniej podatny na błędy niż YAML, ale słabiej wspiera głęboko zagnieżdżone struktury (lista adapterów, z których każdy ma zagnieżdżoną sekcję SMTP/IMAP) — składnia robi się nieczytelna przy zagnieżdżeniu powyżej 2 poziomów.
- YAML wspiera komentarze, naturalnie wyraża strukturę hierarchiczną (Adapters → adapter → SMTP/IMAP) oraz listy (Routing Rules), i jest formatem powszechnie rozpoznawanym w narzędziach operacyjnych (Docker Compose, Kubernetes, GitHub Actions, Ansible) — mniejsza bariera wejścia dla administratora.

## Decyzja

Kanonicznym, human-edytowalnym formatem pliku konfiguracyjnego midoMAIL 2.0 jest **YAML**.

Import/Eksport w UI (65-Konfiguracja.md) operuje na plikach YAML jako formacie domyślnym. Model konfiguracji w pamięci (SPEC-0005-Configuration-Model.md) jest niezależny od formatu pliku — parser YAML jest jedynym miejscem znającym szczegóły składni.

## Konsekwencje

- Pełny przykładowy plik konfiguracyjny oraz tabela typów/wartości domyślnych/zakresów są częścią SPEC-0005-Configuration-Model.md.
- Walidacja pliku YAML (w tym walidacja krzyżowa pomiędzy sekcjami) zachodzi przed jego zastosowaniem — nigdy w trakcie odczytu przez komponenty Core (30-Infrastructure/30-Konfiguracja.md, §4).
- Znane pułapki YAML (np. niejawna konwersja `no`/`yes`/`on`/`off` na wartości logiczne — tzw. „Norway problem") muszą być jawnie zablokowane przez parser (tryb ścisły/YAML 1.2) na etapie implementacji.
- Eksport do innych formatów (np. JSON jako format wymiany między systemami) jest dopuszczalny jako funkcja dodatkowa, ale nie zastępuje YAML jako formatu kanonicznego.

## Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 30-Infrastructure/30-Konfiguracja.md
- 60-User-Interface/65-Konfiguracja.md
- 91-Specification/SPEC-0005-Configuration-Model.md
