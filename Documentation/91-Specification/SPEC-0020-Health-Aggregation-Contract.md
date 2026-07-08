# SPEC-0020 — Health Aggregation Contract

**Status:** Accepted
**Powiązane dokumenty:** 10-Core/14-Registry-Adapterow.md, 30-Infrastructure/35-Health-Monitor.md, SPEC-0015-Adapter-Observability-Contract.md, SPEC-0018-Alert-Model-Contract.md

---

# Cel

35-Health-Monitor.md §5 wymaga „okresowego sprawdzania stanu" i „definiowania progów ostrzegawczych", ale nie precyzuje reguły agregacji — jak N niezależnych `HealthStatus` (SPEC-0015, per-adapter) staje się jednym stanem systemowym i kiedy to przejście generuje `Alert` (SPEC-0018). Rozstrzygane tutaj, przed Iteracją 4.3.

---

# Skąd HealthMonitor bierze adaptery do sprawdzenia

10-Core/14-Registry-Adapterow.md §5 wymienia Health Monitor jako współpracownika Registry — ale `Registry` (`domain/adapter/Registry.kt`) świadomie przechowuje wyłącznie `AdapterLifecycle` (start/stop) i stan cyklu życia, nie pełny `Adapter` (health/metrics/send). Ten sam problem — „potrzebuję żywej instancji `Adapter` po `AdapterId`, a Registry jej nie daje" — pojawił się już raz w Fazie 3 i został rozwiązany przez odrębny komponent kompozycyjny (`AdapterRegistryOutbound` w `:platform-android`), nie przez rozszerzenie `Registry`.

**Decyzja:** `HealthMonitor` przyjmuje `List<Adapter>` bezpośrednio w konstruktorze, dostarczoną przez punkt kompozycji (ten sam, który tworzył te adaptery przez `AdapterFactory.create()` i i tak już je trzyma — Iteracja 4.13, `GatewayForegroundService`). `HealthMonitor` nie odpytuje `Registry` o stan/instancje — „współpraca z Registry" wymagana przez 14-Registry-Adapterow.md §5 jest spełniona przez współdzielony `EventPublisher`/Event Bus (10-Core/15-Event-Bus.md: oba komponenty publikują zdarzenia do tej samej szyny), nie przez bezpośrednie wywołania metod. `Registry` pozostaje niezmieniony.

---

# Reguła agregacji

Stan systemowy Health Monitor to funkcja zbioru `HealthStatus` wszystkich dostarczonych adapterów, ewaluowana przy każdym cyklu `SchedulerProvider`:

- **Wszystkie `healthy = true`** → stan systemowy `HEALTHY`.
- **Co najmniej jeden `healthy = false`** → stan systemowy `DEGRADED` (reguła „worst-of" — jeden niezdrowy komponent wystarcza, żeby cały system przestał być w pełni sprawny; zgodne z 35-Health-Monitor.md §2: „ocena gotowości i dostępności platformy" jako całości, nie średnia ważona).

Próg „ostrzegawczy" (35-Health-Monitor.md §5) nie jest liczbowy (np. odsetek niezdrowych adapterów) — przy typowej instalacji z małą liczbą adapterów (Faza 1–3: jeden Email + jeden GSM) średnia/próg procentowy nie ma sensownej interpretacji; **każde pojedyncze przejście healthy→unhealthy jednego adaptera jest znaczące samo w sobie**.

`Alert` jest generowany wyłącznie przy **przejściu** stanu (healthy→unhealthy lub unhealthy→healthy), nie przy każdym cyklu sprawdzania — analogicznie do `Registry.setState()` publikującego zdarzenie tylko przy zmianie, nie przy każdym odczycie. Przejście na niezdrowy generuje `Alert(level = WARNING, status = ACTIVE)`; powrót do zdrowego generuje `Alert(level = INFO, status = RESOLVED)` odnoszący się do tego samego `source`.

---

# Wspólny punkt wejścia z Error Handling

00-Foundation/06-Glossary.md: „Alert — generowane przez Health Monitor **lub** Error Handling". `HealthMonitor` publikuje wygenerowane `Alert` przez pojedynczy, wspólny port:

```kotlin
fun interface AlertSink {
    fun onAlert(alert: Alert)
}
```

Ten sam port będzie używany przez klasyfikację błędów (Iteracja 4.4b, `GatewayError.CriticalError`) — jeden punkt wejścia dla obu źródeł Alertów, nie dwie rozbieżne ścieżki.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/14-Registry-Adapterow.md
- 10-Core/15-Event-Bus.md
- 30-Infrastructure/35-Health-Monitor.md
- 91-Specification/SPEC-0015-Adapter-Observability-Contract.md
- 91-Specification/SPEC-0018-Alert-Model-Contract.md
