# ADR-0006 — Architektura Rate Limiting

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

Żaden dokument nie definiował modelu ograniczania przepustowości (rate limiting), mimo że jest to twarde wymaganie operacyjne dla produkcji: rzeczywiste transporty (SMTP/IMAP dostawcy pocztowego, sieć GSM operatora, zewnętrzne API REST) narzucają własne limity częstotliwości operacji, a ich przekroczenie kończy się blokadą konta lub odrzuceniem żądań przez system zewnętrzny — poza kontrolą Gateway. Brak modelu rate limitingu oznaczałby zaprojektowanie go dopiero w reakcji na pierwszą blokadę konta na produkcji.

## Decyzja

### Zakres ograniczania

Rate limiting działa **per adapter**, z opcjonalnym doprecyzowaniem **per operacja** (osobne limity dla `send` i dla odbioru/pollingu — realne limity SMTP i IMAP/GSM różnią się dla wysyłki i odbioru). Nie wprowadza się ograniczania **per tenant** — model domenowy midoMAIL 2.0 nie zawiera pojęcia „tenant", co jest osobną, jawną decyzją (90-ADR/ADR-0008-Multi-Tenancy.md), nie przeoczeniem tego ADR.

### Algorytm

Token bucket (kubełek żetonów) per adapter/operacja — pojemność i tempo uzupełniania są parametrami konfiguracyjnymi (nie są przedmiotem tego ADR, zgodnie z prośbą: architektura, nie konkretne liczby). Token bucket jest wybrany, ponieważ w naturalny sposób:

- wygładza chwilowe skoki (burst) w granicach pojemności kubełka,
- pozwala wyliczyć dokładny czas oczekiwania do odzyskania zdolności (kiedy pojawi się kolejny żeton) — wartość ta jest przekazywana do Schedulera jako opóźnienie ponowienia.

### Zachowanie po przekroczeniu limitu

**Backpressure przez Scheduler, nie odrzucenie i nie cichy dropout.** Gdy adapter zgłasza brak dostępnych żetonów, komunikat pozostaje w stanie `Scheduled`/`Waiting` (SPEC-0004-Processing-State.md) i jest ponawiany przez Scheduler po czasie wyznaczonym przez token bucket — nie jest to ten sam mechanizm co Retry po błędzie transportu (34-Error-Handling.md), lecz planowane opóźnienie o znanej długości. Odrzucenie komunikatu z powodu limitu przepustowości nigdy nie następuje automatycznie; jeśli polityka operacyjna wymaga odrzucenia po przekroczeniu maksymalnego czasu oczekiwania, jest to jawna, konfigurowalna reguła, nie zachowanie domyślne.

### Współpraca z Exactly Once

Rate limiting działa **na etapie dostarczenia do transportu (wywołanie `Adapter.send()`), już po zarejestrowaniu ExternalReference** przez Exactly Once (10-Core/17-Exactly-Once.md; 91-Specification/SPEC-0009-Message-Store-Contract.md, §Atomowość). Ponowienie wywołane przez rate limiting dotyczy tego samego, już zarejestrowanego `GatewayMessage`/`MessageId` — nie tworzy nowego rekordu ani nie wywołuje ponownie `insertIfAbsent`. Rate limiting nigdy nie jest przyczyną duplikatu, ponieważ nie wpływa na etap rejestracji komunikatu, wyłącznie na etap jego dostarczenia.

## Konsekwencje

- Adapter raportuje swój stan ograniczenia przepustowości jako część `HealthStatus`/`Metrics` (91-Specification/SPEC-0006-Adapter-Contract.md) — throttling jest widoczny w Health Monitor i Dashboard, nie jest ukrytym stanem wewnętrznym.
- Pełny kontrakt techniczny (interfejs, konfiguracja, wpływ na Processing State) jest zdefiniowany w SPEC-0011-Rate-Limiting-Contract.md.
- Rozszerzenie o wymiar „per tenant" pozostaje możliwe w przyszłości bez zmiany obecnego kontraktu — wymaga jedynie dodania pojęcia Tenant do modelu domenowego.

## Dokumenty powiązane

- 00-Foundation/03-Model-domenowy.md
- 00-Foundation/06-Glossary.md
- 10-Core/17-Exactly-Once.md
- 30-Infrastructure/34-Error-Handling.md
- 90-ADR/ADR-0008-Multi-Tenancy.md
- 91-Specification/SPEC-0004-Processing-State.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md
