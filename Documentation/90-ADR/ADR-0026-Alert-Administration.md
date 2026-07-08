# ADR-0026 — Administracja alertów

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`60-User-Interface/66-Monitoring.md` §3-4 wymaga listowania aktywnych alertów (poziom, źródło, czas, status, zalecane działanie) i ich potwierdzania z UI — potwierdzenie zatrzymuje eskalację natychmiast. `71-Widoki-mobilne.md` §3 wymaga tego samego dla widoku mobilnego.

Mechanizm już istnieje: `EscalationScheduler` (`domain/notification/EscalationScheduler.kt`, Faza 4) śledzi aktywne alerty w `activeAlerts: ConcurrentHashMap<AlertId, Tracked>` i udostępnia `acknowledge(alertId: AlertId)`, który usuwa alert ze śledzenia i zatrzymuje eskalację (38-Powiadomienia.md §5: „Eskalacja zatrzymuje się w momencie potwierdzenia"). Brakuje wyłącznie sposobu na ODCZYT listy aktywnych alertów z zewnątrz — pole `activeAlerts` jest prywatne.

## Decyzja

Amendment `EscalationScheduler` — jedna nowa, publiczna metoda odczytu:

```kotlin
fun activeAlerts(): List<Alert> = activeAlerts.values.map { it.alert }.toList()
```

Żaden nowy port/klasa — `EscalationScheduler` nie jest zamrożonym kontraktem (w przeciwieństwie do `RoutingEngine`/`GatewayEngine`), więc amendment bezpośredni jest właściwy, tym samym duchem co `Registry.registeredAdapterIds()` (ADR-0018, Faza 5) i `HealthMonitor` w Fazie 4 — dodanie odczytu do istniejącego, mutowalnego komponentu infrastrukturalnego, nie tworzenie nowej warstwy pośredniej bez potrzeby.

`acknowledge(alertId)` już istnieje i nie wymaga zmian — Admin API (Iteracja 6.9) wywołuje go bezpośrednio.

## Konsekwencje

- `EscalationScheduler` pozostaje jedynym źródłem prawdy o tym, które alerty są aktywne i śledzone do eskalacji — Admin API nie duplikuje tego stanu.
- `activeAlerts()` zwraca migawkę (kopia listy) w momencie wywołania — brak żywego referencjonowania wewnętrznej mapy, zgodnie z konwencją `Registry.registeredAdapterIds()`/`RoutingRuleAdministration.list()`.
- `activeAlerts()` zawiera KAŻDY Alert przekazany do `register()` ze statusem `ACTIVE`, niezależnie od tego, czy jego poziom ma skonfigurowany próg eskalacji (`escalateAfterMillis` wpływa wyłącznie na to, czy Alert jest faktycznie ponownie dostarczany, nie na to, czy jest widoczny w tej metodzie). Alerty, które NIGDY nie zostały przekazane do `register()` (np. jeśli punkt kompozycji nie wywoła tego dla danego źródła Alertów) nie pojawią się — to świadome ograniczenie tego mechanizmu. Pełna lista WSZYSTKICH kiedykolwiek wygenerowanych alertów pozostaje dostępna przez `EventStore`/`DiagnosticsFacade` (Faza 4), nie przez ten port. Admin API (Iteracja 6.9) dokumentuje to rozróżnienie wprost w SPEC-0025.

## Dokumenty powiązane

- 30-Infrastructure/38-Powiadomienia.md
- 60-User-Interface/66-Monitoring.md
- 60-User-Interface/71-Widoki-mobilne.md
- 91-Specification/SPEC-0018-Alert-Model-Contract.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
