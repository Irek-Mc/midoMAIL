# ADR-0028 — Ręczne ponowne przetworzenie komunikatu

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`60-User-Interface/62-Komunikaty.md` §5 wymaga operacji „Ponowne przetworzenie", z zastrzeżeniem: „nie omija Exactly Once... świadomie unieważnia istniejący rekord deduplikacji dla danego ExternalReference i rejestruje to unieważnienie jako odrębne zdarzenie domenowe — w odróżnieniu od automatycznego ponownego przetworzenia przez adapter, które Exactly Once zawsze blokuje." `91-Specification/SPEC-0008-Exactly-Once-Contract.md` §Ręczne ponowne przetworzenie opisuje tę operację słownie od Fazy 1, ale nigdy nie została zaimplementowana.

`GatewayEngine` jest zamrożony (niezmieniony od Iteracji 10b) — jedyny punkt wejścia to `GatewayInbound.receive(message)`, wymagający komunikatu w stanie `ProcessingState.ACCEPTED`. Krok 4 tego przepływu (`ExactlyOnceEngine.processIfNew`) wywołuje `MessageStore.insertIfAbsent(externalReference, message)` — bez usunięcia istniejącego wpisu `externalReference→messageId`, każda ponowna próba z tym samym `ExternalReference` zawsze zwróci `ALREADY_EXISTS` (poprawne, zamierzone zachowanie automatycznego Exactly Once). Ręczne ponowne przetworzenie wymaga zatem jawnego usunięcia tego wpisu PRZED wywołaniem `receive()` ponownie — nie omijania `ExactlyOnceEngine`, tylko świadomej zmiany stanu, którą on odczytuje.

## Decyzja

**Amendment `MessageStore`** (SPEC-0009) — jedna nowa metoda, addytywna (nie zmienia istniejących sygnatur, tym samym duchem co amendment `ConfigurationProvider` w Fazie 5):

```kotlin
interface MessageStore {
    // ...istniejące metody bez zmian...
    fun invalidateDeduplication(externalReference: ExternalReference)
}
```

`InMemoryMessageStore` usuwa wyłącznie wpis z wewnętrznej mapy `externalReference→messageId` — ZACHOWUJE oryginalny rekord `GatewayMessage` w `recordsByMessageId` (dostępny nadal przez `findById`, diagnostyka/ślad komunikatu pozostają nienaruszone).

**Nowa klasa administracyjna** w `:domain.administration`:

```kotlin
class MessageReprocessingAdministration(
    private val messageStore: MessageStore,
    private val gatewayInbound: GatewayInbound
) {
    sealed class ReprocessResult {
        data class Reprocessed(val message: GatewayMessage) : ReprocessResult()
        data object NotFound : ReprocessResult()
        data class Failed(val reason: String) : ReprocessResult()
    }

    fun reprocess(externalReference: ExternalReference): ReprocessResult
}
```

Kroki `reprocess()`:
1. `messageStore.findByExternalReference(externalReference)` — brak wyniku → `NotFound`.
2. `messageStore.invalidateDeduplication(externalReference)` — jawne unieważnienie rekordu (SPEC-0008 dosłownie).
3. Budowa NOWEGO `GatewayMessage` — ta sama `source`/`destination`/`payload`, NOWY `MessageId`, `CausationId` ustawiony na oryginalny `MessageId` (wiąże ponowną próbę z pierwotnym komunikatem), świeży `ProcessingContext()` (stan `ACCEPTED`).
4. `gatewayInbound.receive(fresh)` — PRZECHODZI przez `ExactlyOnceEngine`/`RoutingEngine` normalnie (żadnego ominięcia), teraz z powodzeniem, bo krok 2 usunął blokadę.

## Konsekwencje

- `GatewayEngine`/`ExactlyOnceEngine`/`RoutingEngine` pozostają w 100% niezmienione — reprocessing nie tworzy alternatywnej ścieżki omijającej ich logikę, dokładnie zgodnie z wymogiem 62-Komunikaty.md („nie omija Exactly Once").
- Jeśli `gatewayInbound.receive()` mimo unieważnienia zwróci `Duplicate` (np. wyścig z inną operacją wstawiającą ten sam `ExternalReference` w międzyczasie), operacja zwraca `Failed` — nie jest to traktowane jako sukces cichy.
- Audyt („rejestruje... jako odrębne zdarzenie domenowe") realizowany na warstwie Admin API (Iteracja 6.10) przez istniejący `AdminAuditRecorder`/`EventCategory.ADMINISTRATIVE` (Faza 5) — nie nowy mechanizm, ten sam duch co inne operacje zapisu.
- `invalidateDeduplication` nie usuwa oryginalnego rekordu z `MessageStore` — historia/diagnostyka (`findById`, `DiagnosticsFacade.messageTrace`) pozostają kompletne dla obu prób (oryginalnej i ponownej), połączone przez `CausationId`.

## Dokumenty powiązane

- 10-Core/11-Gateway-Engine.md
- 10-Core/17-Exactly-Once.md
- 60-User-Interface/62-Komunikaty.md
- 91-Specification/SPEC-0008-Exactly-Once-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
