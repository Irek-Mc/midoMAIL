# SPEC-0022 — Error Classification Contract

**Status:** Accepted
**Powiązane dokumenty:** 30-Infrastructure/34-Error-Handling.md, 00-Foundation/06-Glossary.md, SPEC-0018-Alert-Model-Contract.md, ADR-0014-Registry-Start-Failure.md

---

# Cel

34-Error-Handling.md §3 wymienia klasy błędów w prozie („błędy domenowe, infrastrukturalne, adapterów, konfiguracji, bezpieczeństwa, krytyczne wymagające interwencji"), bez typów/hierarchii. §4 wymaga „jednoznacznej klasyfikacji" i „identyfikatorów korelacyjnych". Rozstrzygane tutaj, przed Iteracją 4.4b.

---

# Model

```kotlin
package midomail.domain.error

sealed class GatewayError {
    abstract val message: String
    abstract val correlationId: midomail.domain.message.CorrelationId?
    abstract val cause: Throwable?

    data class DomainError(
        override val message: String,
        override val correlationId: midomail.domain.message.CorrelationId?,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class InfrastructureError(
        override val message: String,
        override val correlationId: midomail.domain.message.CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class AdapterError(
        val adapterId: midomail.domain.adapter.AdapterId,
        override val message: String,
        override val correlationId: midomail.domain.message.CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class ConfigurationError(
        override val message: String,
        override val correlationId: midomail.domain.message.CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class SecurityError(
        override val message: String,
        override val correlationId: midomail.domain.message.CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()

    data class CriticalError(
        override val message: String,
        override val correlationId: midomail.domain.message.CorrelationId? = null,
        override val cause: Throwable? = null
    ) : GatewayError()
}
```

Sześć podtypów odpowiada dosłownie sześciu klasom z 34-Error-Handling.md §3 — zamknięta taksonomia, rozszerzenie wymaga nowego ADR (wzorem `AlertLevel`/`HealthStatus`).

`RateLimiter`/`Metrics.throttledCount` (34-Error-Handling.md §6: „Throttling nie jest błędem") **nie ma** odpowiednika w `GatewayError` — celowo, throttling pozostaje odrębnym, już istniejącym mechanizmem (`Metrics.throttledCount`/`cumulativeThrottlingEvents`), nie jednym z podtypów tego błędu.

---

# Punkt integracji z Alertami

`CriticalError` woła ten sam `AlertSink.onAlert` co `HealthMonitor` (SPEC-0020) — jeden punkt wejścia dla obu źródeł Alertów (Glosariusz: „Alert — generowane przez Health Monitor **lub** Error Handling"). Mapowanie: `CriticalError` → `Alert(level = CRITICAL, status = ACTIVE)`.

---

# Gdzie klasyfikacja zachodzi (nie modyfikujemy zamrożonych kontraktów)

`GatewayEngine`/`Registry` są zamrożonymi kontraktami Core (Faza 1). Klasyfikacja błędów **nie** modyfikuje ich wewnętrznej logiki — zachodzi w punkcie, który już dziś łapie ich wyjątki/wyniki:

- `Registry.register()` już łapie wyjątek z `adapter.start()` i przechodzi do `FAILED` przed ponownym rzuceniem (ADR-0014) — **wywołujący** (punkt kompozycji, dokładnie ten sam `registerSafely()` zbudowany w Fazie 3, Iteracja 3.13, `GatewayForegroundService`) jest miejscem, gdzie złapany wyjątek zostaje dodatkowo sklasyfikowany jako `GatewayError.CriticalError` i przekazany do `AlertSink` — bez zmiany w `Registry` samym.
- `GatewayEngine.receive()` zwraca `ProcessingResult.Rejected`/`NoRoute` (nie rzuca wyjątku) — wywołujący (np. `GsmAdapter`'s opakowany `GatewayInbound`, `EmailAdapter.pollOnce()`) inspekcjonuje zwrócony `ProcessingResult` i klasyfikuje `Rejected`/`NoRoute` jako `GatewayError.DomainError`, bez zmiany w `GatewayEngine`.

Ta sama zasada co przy `AlertSink`/`HealthMonitor` (SPEC-0020) — rozszerzanie odpowiedzialności przez dodanie NOWEGO, cienkiego komponentu w punkcie kompozycji, nie przez poszerzanie zamrożonych kontraktów Core.

---

# Zasady zgodności

Rozszerzenie hierarchii `GatewayError` o nowy podtyp wymaga nowego ADR.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 30-Infrastructure/34-Error-Handling.md
- 90-ADR/ADR-0014-Registry-Start-Failure.md
- 91-Specification/SPEC-0018-Alert-Model-Contract.md
- 91-Specification/SPEC-0020-Health-Aggregation-Contract.md
