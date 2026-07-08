# SPEC-0011 — Rate Limiting Contract

**Status:** Accepted
**Powiązane dokumenty:** 90-ADR/ADR-0006-Rate-Limiting.md, 10-Core/18-Porty.md, 91-Specification/SPEC-0006-Adapter-Contract.md

---

# Cel

Dokument definiuje techniczny kontrakt portu Rate Limiter oraz jego integrację z adapterami, Schedulerem i Exactly Once — realizując decyzję ADR-0006-Rate-Limiting.md.

---

# Zakres

- Rate limiting działa **per adapter**, opcjonalnie doprecyzowany **per operacja** (`send` / `receive` osobno).
- Nie obejmuje wymiaru „per tenant" — brak takiego pojęcia w modelu domenowym (00-Foundation/03-Model-domenowy.md).

---

# Interfejs

```kotlin
interface RateLimiter {
    // Zwraca Allowed natychmiast, jeśli dostępny jest żeton; w przeciwnym razie
    // Throttled z wyliczonym czasem do odzyskania zdolności.
    fun tryAcquire(adapterId: AdapterId, operation: RateLimitedOperation): RateLimitDecision
}

enum class RateLimitedOperation { SEND, RECEIVE }

sealed class RateLimitDecision {
    object Allowed : RateLimitDecision()
    data class Throttled(val retryAfterMillis: Long) : RateLimitDecision()
}
```

`RateLimiter` jest portem (10-Core/18-Porty.md, kategoria porty infrastrukturalne) — implementacja (np. token bucket w pamięci lub trwały licznik) jest szczegółem infrastruktury, nieznanym Gateway Engine.

---

# Algorytm — token bucket

Każda para (adapterId, operation) posiada własny kubełek żetonów o konfigurowalnej pojemności i tempie uzupełniania (91-Specification/SPEC-0005-Configuration-Model.md, §rateLimiting). `tryAcquire` pobiera żeton, jeśli dostępny; w przeciwnym razie wylicza `retryAfterMillis` na podstawie tempa uzupełniania kubełka.

---

# Integracja z przepływem przetwarzania

1. Gateway Engine wyznacza trasę (Routing) i przekazuje komunikat do `Adapter.send()`.
2. Przed faktycznym wywołaniem transportu, adapter (lub warstwa pośrednia między Gateway Engine a adapterem) wywołuje `RateLimiter.tryAcquire(adapterId, SEND)`.
3. Jeśli `Allowed` — wysyłka przebiega normalnie.
4. Jeśli `Throttled(retryAfterMillis)` — komunikat pozostaje w Processing State `Scheduled` (SPEC-0004-Processing-State.md) i Scheduler planuje ponowną próbę po `retryAfterMillis` (10-Core/16-Scheduler.md). Nie jest to Retry po błędzie (34-Error-Handling.md) — throttling nie jest błędem, jest planowanym opóźnieniem o znanym czasie.
5. Kroki 2–4 powtarzają się aż do uzyskania `Allowed` lub przekroczenia (opcjonalnego, konfigurowalnego) maksymalnego czasu oczekiwania, po którym komunikat przechodzi w stan `Failed` z jawną przyczyną „limit przepustowości przekroczony" (widoczną w Diagnostyce, 60-User-Interface/67-Diagnostyka.md).

---

# Współpraca z Exactly Once

`RateLimiter.tryAcquire()` jest wywoływane **po** zarejestrowaniu `ExternalReference` przez Exactly Once (10-Core/17-Exactly-Once.md, §Cykl przetwarzania, krok 2) — throttling nigdy nie poprzedza rejestracji komunikatu i nigdy nie prowadzi do ponownego wywołania `insertIfAbsent` dla tego samego zdarzenia źródłowego. Ponowienie z powodu throttlingu operuje na już zarejestrowanym `MessageId` — nie może wytworzyć duplikatu.

---

# Obserwowalność

Adapter raportuje bieżący stan swojego rate limitera jako część `Metrics` (91-Specification/SPEC-0006-Adapter-Contract.md, §Minimalny kontrakt): liczbę dostępnych żetonów, liczbę komunikatów aktualnie throttled, skumulowaną liczbę zdarzeń throttlingu. Health Monitor traktuje długotrwałe throttlingowanie (powyżej konfigurowalnego progu) jako sygnał degradacji adaptera (`Degraded`, 10-Core/14-Registry-Adapterow.md, §4).

---

# Dokumenty powiązane

- 00-Foundation/03-Model-domenowy.md
- 00-Foundation/06-Glossary.md
- 10-Core/14-Registry-Adapterow.md
- 10-Core/16-Scheduler.md
- 10-Core/17-Exactly-Once.md
- 10-Core/18-Porty.md
- 30-Infrastructure/34-Error-Handling.md
- 90-ADR/ADR-0006-Rate-Limiting.md
- 91-Specification/SPEC-0004-Processing-State.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
