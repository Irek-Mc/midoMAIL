# SPEC-0019 — Notification Channel Contract

**Status:** Accepted
**Powiązane dokumenty:** ADR-0016-Notification-Channel-Port.md, 30-Infrastructure/38-Powiadomienia.md, SPEC-0018-Alert-Model-Contract.md

---

# Cel

ADR-0016 rozstrzygnął, że `NotificationChannel` jest odrębnym portem, nie `Adapter`. Ten dokument definiuje dokładny kształt tego portu i typu wyniku — 38-Powiadomienia.md §2 wymaga „raportowania powodzenia/niepowodzenia samej dostawy powiadomienia", bez podania typu.

---

# Model

```kotlin
package midomail.domain.notification

interface NotificationChannel {
    fun deliver(alert: midomail.domain.health.Alert): NotificationResult
}

sealed class NotificationResult {
    data object Delivered : NotificationResult()
    data class Failed(val reason: String) : NotificationResult()
    data class Unavailable(val reason: String) : NotificationResult()
}
```

`Delivered`/`Failed` — wynik faktycznej próby dostarczenia (np. e-mail wysłany / SMTP zwrócił błąd; webhook zwrócił 2xx / webhook zwrócił błąd po wyczerpaniu prób Retry). `Unavailable` — kanał nie może w ogóle podjąć próby na tej platformie (np. Push na headless JVM/Linux, 38-Powiadomienia.md §3) — odróżnione od `Failed`, ponieważ nie jest to niepowodzenie transportu, tylko brak możliwości platformowej, nie podlegający Retry (34-Error-Handling.md §5/§6: dotyczy wyłącznie faktycznych prób dostarczenia).

---

# Niezawodność (38-Powiadomienia.md §6)

Niepowodzenie dostarczenia powiadomienia (`Failed`) nigdy nie blokuje głównego przetwarzania komunikatów przez Gateway Engine — `deliver()` zwraca wynik, nie rzuca wyjątku poza granicę portu (implementacje łapią własne wyjątki transportu i mapują je na `Failed`).

---

# Zasady zgodności

Rozszerzenie `NotificationResult` o nowy podtyp wymaga nowego ADR.

---

# Dokumenty powiązane

- 90-ADR/ADR-0016-Notification-Channel-Port.md
- 30-Infrastructure/34-Error-Handling.md
- 30-Infrastructure/38-Powiadomienia.md
- 91-Specification/SPEC-0018-Alert-Model-Contract.md
