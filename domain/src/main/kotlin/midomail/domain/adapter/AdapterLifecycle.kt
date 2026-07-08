package midomail.domain.adapter

/**
 * Wąski kontrakt cyklu życia adaptera wymagany przez Registry (SPEC-0014-Adapter-Lifecycle-Contract.md,
 * §Zakres w Fazie 1) — podzbiór pełnego interfejsu `Adapter` z SPEC-0010-Plugin-SDK-Contract.md,
 * ograniczony do metod, które Registry faktycznie wywołuje podczas przejść stanu.
 */
interface AdapterLifecycle {
    fun start()
    fun stop()
}
