# ADR-0027 — Resource Monitor (monitoring zasobów hosta)

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`60-User-Interface/66-Monitoring.md` §2 i `61-Dashboard.md` §2 (sekcja „Monitoring") wymagają wglądu w CPU, RAM, Storage, Network — metryki HOSTA/PROCESU, nie zdrowia adapterów. Faza 4 zbudowała wyłącznie `HealthMonitor` (agregacja `HealthStatus` per adapter — SPEC-0020) i `StatisticsAggregator` (metryki komunikatów — SPEC-0021); żaden istniejący port nie modeluje zasobów systemowych. Genuinie nowa zdolność.

`:platform-jvm` nie istnieje do Fazy 7 (55-Roadmap.md §9) — implementacja może dziś powstać wyłącznie dla `:platform-android`.

## Decyzja

Nowy port w `:domain`:

```kotlin
data class ResourceSnapshot(
    val cpuUsagePercent: Double? = null,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val storageUsedBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val networkBytesReceived: Long? = null,
    val networkBytesSent: Long? = null
)

fun interface ResourceMonitor {
    fun snapshot(): ResourceSnapshot
}
```

Wszystkie pola nullable — nie każda platforma/uprawnienie udostępnia każdą metrykę (ten sam duch co `HealthStatus.details`/`NotificationResult.Unavailable`: brak wartości jest jawnie reprezentowany, nie zerem czy wyjątkiem).

**Implementacja `:platform-android`** (Iteracja 6.2, ta sama iteracja — konkretny konsument istnieje od razu, zgodnie z zasadą „port + realny konsument w tej samej iteracji" z Faz 2-5):
- RAM — `ActivityManager.getMemoryInfo(MemoryInfo)` (`availMem`/`totalMem`).
- Storage — `StatFs` na katalogu danych aplikacji (`blockSizeLong * availableBlocksLong`/`blockCountLong`).
- Network — `TrafficStats.getTotalRxBytes()`/`getTotalTxBytes()`.
- **CPU — jawnie `null`.** Android od wersji 8 (API 26) ogranicza dostęp do `/proc/stat` dla aplikacji bez uprawnień systemowych — nie ma publicznego, niewymagającego uprawnień specjalnych API do odczytu ogólnego zużycia CPU hosta. Udokumentowane jako ograniczenie platformy, nie luka implementacyjna.

**Brak implementacji JVM w tej fazie (SA-14)** — jawny stub w `:domain` (ten sam wzorzec co `PushNotificationChannel`, Faza 4):

```kotlin
class UnavailableResourceMonitor : ResourceMonitor {
    override fun snapshot(): ResourceSnapshot = ResourceSnapshot()
}
```

Prawdziwa implementacja JVM/Linux (np. `OperatingSystemMXBean`, `File.getUsableSpace()`) dochodzi wraz z `:platform-jvm` w Fazie 7.

## Konsekwencje

- Dashboard/Monitoring (Iteracje 6.18-6.19) muszą jawnie renderować „niedostępne" dla pól `null` — nie zero, nie pominięcie, zgodnie z 66-Monitoring.md §5: „UI nie interpretuje stanu komponentów samodzielnie".
- Brak CPU na Androidzie jest ograniczeniem PLATFORMY (udokumentowanym), nie długiem architektonicznym do spłacenia — nie ma akcji do podjęcia w przyszłej fazie.
- `UnavailableResourceMonitor` w `:domain` (nie w hipotetycznym `:platform-jvm`, który jeszcze nie istnieje) pozwala Admin API/harnessowi (Iteracja 6.11) działać już teraz bez czekania na Fazę 7.

## Dokumenty powiązane

- 30-Infrastructure/35-Health-Monitor.md
- 60-User-Interface/61-Dashboard.md
- 60-User-Interface/66-Monitoring.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
