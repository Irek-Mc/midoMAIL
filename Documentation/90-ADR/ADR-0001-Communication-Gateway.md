# ADR-0001 — Communication Gateway jako produkt

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

Wersja 1.x była rozwijana głównie jako aplikacja SMS z funkcjami integracyjnymi. Zdobyte doświadczenia wykazały, że właściwym produktem nie jest aplikacja, lecz uniwersalny silnik integracyjny.

## Decyzja

midoMAIL 2.0 jest projektowany jako Communication Gateway.

Gateway stanowi rdzeń produktu.

Transporty, adaptery oraz platformy uruchomieniowe są elementami wymienialnymi i nie wpływają na model domenowy ani logikę biznesową.

## Konsekwencje

- Core pozostaje niezależny od Androida.
- Core pozostaje niezależny od SMS, Email i innych transportów.
- Wszystkie integracje realizowane są przez Porty i Plugin SDK.
- Rozwój produktu koncentruje się na architekturze Gateway, a nie na pojedynczych kanałach komunikacji.

## Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 00-Foundation/01-Wizja-produktu.md
- 00-Foundation/02-Zalozenia-architektoniczne.md
- 10-Core/10-Architektura-systemu.md