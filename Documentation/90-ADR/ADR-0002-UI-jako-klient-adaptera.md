# ADR-0002 — UI jako klient Adaptera REST/CLI

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

Warstwa 60-User-Interface wprowadziła Dashboard i panel administracyjny bez jednoznacznego rozstrzygnięcia, w jaki sposób UI dociera do Communication Gateway. Wczesna wersja 60-UX-Filozofia.md stwierdzała jednocześnie, że „UI nie komunikuje się bezpośrednio z adapterami" oraz że „operacje administracyjne są wykonywane poprzez kontrakty Core" — nie precyzując, czy UI jest odrębnym adapterem, klientem istniejącego adaptera, czy procesem mającym bezpośredni dostęp do portów Core. Ta niejednoznaczność jest analogiczna do błędu routingu po `sender.contains("@")` z wersji 1.x: brak jawnej decyzji architektonicznej prowadzący do improwizacji podczas implementacji.

## Decyzja

UI **nie jest** adapterem i **nie jest** rejestrowane w Registry Adapterów. UI jest klientem **Adaptera REST** lub **Adaptera CLI** — wyłącznie poprzez ich publiczne kontrakty (SPEC-0006-Adapter-Contract). UI nigdy nie wywołuje Gateway Engine ani portów Core bezpośrednio, ani nie komunikuje się z adapterami transportowymi (GSM, Email, WebSocket).

Widoki mobilne (71-Widoki-mobilne.md) korzystają z tych samych kontraktów co pełny panel — nie ma odrębnej ścieżki integracji dla urządzeń mobilnych.

## Konsekwencje

- Dodanie UI nie wymaga zmian w Gateway Engine, Routing Engine ani Registry Adapterów — potwierdza zasadę rozszerzalności bez modyfikacji rdzenia (02-Zalozenia-architektoniczne, §5).
- Adapter REST i Adapter CLI muszą udostępniać pełny zestaw operacji administracyjnych wymaganych przez UI (odczyt stanu, zarządzanie adapterami, konfiguracja, routing) — patrz SPEC-0006.
- UI nie może powstać przed Adapterem REST i Adapterem CLI (patrz 55-Roadmap, Faza 5 i 6).
- Każda operacja zapisu wykonana przez UI (np. „Ponowne przetworzenie" komunikatu) przechodzi przez tę samą walidację i politykę Exactly Once co każde inne wywołanie Adaptera REST/CLI — UI nie ma uprzywilejowanej ścieżki omijającej te reguły.

## Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 20-Adapters/23-Adapter-REST.md
- 20-Adapters/25-Adapter-CLI.md
- 60-User-Interface/60-UX-Filozofia.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 50-Quality/55-Roadmap.md
