# ADR-0014 — Registry musi łapać wyjątek z Adapter.start()

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

Podczas ręcznej weryfikacji produkcyjnej Fazy 2 na rzeczywistym Gmailu (docs/faza2-weryfikacja-gmail.md, scenariusz 19 — utrata połączenia) ujawniono, że `Registry.register()` (Faza 1, Iteracja 8b, zamrożony Public API) wywołuje `adapter.start()` bez żadnego `try/catch`. Gdy `start()` rzuca wyjątek (np. `EmailAdapter` nie może połączyć się z IMAP z powodu braku sieci), wyjątek leci nieobsłużony przez cały stos wywołań, kończąc proces.

`SPEC-0014-Adapter-Lifecycle-Contract.md`, §Tabela przejść wprost przewiduje przejście `Initializing → Failed` jako dozwolone i udokumentowane od Fazy 1 — ale przed tą poprawką było ono **nieosiągalne w praktyce**, ponieważ nic w kodzie nigdy go nie wywoływało.

## Decyzja

`Registry.register()` opakowuje `adapter.start()` w `try/catch`. W przypadku wyjątku:
1. Wykonuje przejście `Initializing → Failed` (publikując zdarzenie `AdapterStateChanged`, zgodnie z już obowiązującą zasadą „każde przejście stanu jest publikowane").
2. Rzuca wyjątek dalej (nie połyka go) — wywołujący (composition root) nadal wie, że rejestracja się nie powiodła i może zareagować (log, alert, ponowna próba).

Dodatkowo: `EmailAdapter.healthy` (Faza 2, Iteracja 2.6) domyślnie startuje jako `false`, nie `true` — adapter nie jest „zdrowy" dopóki `start()` faktycznie się nie powiedzie. Wcześniej, jeśli `start()` rzucił wyjątek przed `healthy.set(true)`, pole pozostawało w swojej początkowej wartości `true`, co wprowadzało w błąd każdego odpytującego `health()` po nieudanym starcie.

## Konsekwencje

- `Registry.register()` nadal rzuca wyjątek przy nieudanym starcie (zachowanie obserwowalne dla wywołującego niezmienione), ale teraz DODATKOWO poprawnie aktualizuje stan wewnętrzny Registry i publikuje zdarzenie — to rozszerzenie zachowania, nie zmiana kontraktu wywołania.
- Test regresyjny odtwarzający dokładnie ten scenariusz (adapter, którego `start()` zawsze rzuca) dodany do `RegistryTest`.

## Dokumenty powiązane

- 10-Core/14-Registry-Adapterow.md
- 91-Specification/SPEC-0014-Adapter-Lifecycle-Contract.md
- docs/faza2-weryfikacja-gmail.md
