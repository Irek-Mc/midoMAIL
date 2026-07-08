package midomail.domain.gateway

import java.time.Instant

/**
 * Wersja i czas startu procesu Gateway (ADR-0034-Dashboard-Status-i-Liczniki.md,
 * 61-Dashboard.md §2 „Status Gateway"). Prosta struktura danych, nie port — brak potrzeby wielu
 * implementacji; wypełniana przez punkt kompozycji przy starcie procesu.
 */
data class GatewayInfo(val version: String, val startedAt: Instant)
