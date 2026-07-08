package midomail.domain.port

import midomail.domain.message.GatewayMessage

/**
 * Wąski port wyjściowy Gateway Engine (10-Core/11-Gateway-Engine.md, §5, krok 7: „Przekazanie
 * komunikatu do portu wyjściowego").
 *
 * Odpowiednik [midomail.domain.adapter.AdapterLifecycle] dla kierunku wyjściowego — podzbiór
 * pełnego interfejsu `Adapter.send()` z SPEC-0010-Plugin-SDK-Contract.md, ograniczony do tego, co
 * Gateway Engine faktycznie wywołuje. Żaden rzeczywisty adapter nie powstaje w Fazie 1
 * (SPEC-0014-Adapter-Lifecycle-Contract.md, §Zakres w Fazie 1 — ta sama zasada zastosowana do
 * kierunku wyjściowego).
 */
interface GatewayOutbound {
    fun send(message: GatewayMessage)
}
