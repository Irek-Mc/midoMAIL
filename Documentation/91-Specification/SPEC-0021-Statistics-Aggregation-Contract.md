# SPEC-0021 — Statistics Aggregation Contract

**Status:** Accepted
**Powiązane dokumenty:** 30-Infrastructure/37-Statystyki.md, 60-User-Interface/68-Statystyki.md, SPEC-0015-Adapter-Observability-Contract.md

---

# Cel

37-Statystyki.md §4 wymaga „definiowania okresów agregacji" bez podania konkretnego okresu/kształtu migawki. 68-Statystyki.md §5 wymaga, żeby zagregowane metryki „przeżyły purge danych źródłowych w Message Store" — czyli statystyki muszą być przechowywane niezależnie od `MessageStore`, nie wyliczane z niego na żądanie. Rozstrzygane tutaj, przed Iteracją 4.10b.

---

# Model

```kotlin
package midomail.domain.statistics

data class MetricsSnapshot(
    val adapterId: midomail.domain.adapter.AdapterId,
    val periodStart: java.time.Instant,
    val periodEnd: java.time.Instant,
    val messagesSent: Long,
    val messagesReceived: Long,
    val errorCount: Long,
    val throttledCount: Long
)
```

Pola odzwierciedlają dosłownie te liczniki z `Metrics` (SPEC-0015), które mają sens jako **przyrost w okresie** (37-Statystyki.md §3: „liczbę przetworzonych komunikatów, przepustowość... liczbę błędów i ponowień") — nie jako migawka wartości skumulowanej. `availableTokens` (chwilowy stan, nie licznik przyrostowy) celowo pominięty w migawce.

---

# Reguła agregacji: przyrost między migawkami, nie wartość skumulowana

`Adapter.metrics()` zwraca liczniki **skumulowane od startu adaptera** (SPEC-0015). Migawka statystyk za okres [T1, T2] to **różnica** wartości skumulowanej na końcu i na początku okresu, nie wartość skumulowana sama w sobie — inaczej „przepustowość w tym okresie" byłaby zawsze równa całkowitej liczbie wiadomości od startu, bez znaczenia dla „planowania pojemności i analizy wydajności" (37-Statystyki.md §1).

Pierwsza migawka po starcie agregatora nie ma poprzedniej wartości do porównania — ustanawia wyłącznie baseline, nie generuje migawki (analogicznie do `HealthMonitor`, który nie generuje Alertu przy pierwszym odczycie bez punktu odniesienia — z tą różnicą, że tam baseline jest sztuczny/optymistyczny, tutaj naturalny/rzeczywisty).

---

# Przechowywanie niezależne od `MessageStore`

Migawki przechowywane we własnej, wewnętrznej strukturze agregatora (in-memory w Fazie 4, zgodnie z ograniczeniem „brak trwałego Message Store" odziedziczonym z Faz 1–2) — nigdy nie wyliczane z `MessageStore` na żądanie. Spełnia to 68-Statystyki.md §5 strukturalnie: nawet gdyby `MessageStore` wykonał `purge`, wcześniej zebrane migawki pozostają dostępne, bo nie są z niego odczytywane.

---

# Dokumenty powiązane

- 30-Infrastructure/37-Statystyki.md
- 60-User-Interface/68-Statystyki.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0015-Adapter-Observability-Contract.md
