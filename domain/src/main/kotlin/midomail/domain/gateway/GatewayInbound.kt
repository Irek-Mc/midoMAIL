package midomail.domain.gateway

import midomail.domain.message.GatewayMessage

/**
 * Port wejściowy Gateway Engine (SPEC-0010-Plugin-SDK-Contract.md, §Porty przekazywane
 * adapterowi) — adapter wywołuje tę metodę, gdy odbierze nowy komunikat z transportu.
 *
 * Umieszczony w pakiecie `gateway`, nie `port`: to Core *implementuje* ten interfejs (przez
 * [GatewayEngine]) i *udostępnia go* adapterom, a nie odwrotnie — w przeciwieństwie do portów
 * takich jak [midomail.domain.port.GatewayOutbound], które Core definiuje, a infrastruktura
 * implementuje. Umieszczenie w `port` stworzyłoby zależność cykliczną (`port` → `gateway` →
 * `port`), ponieważ [ProcessingResult] jest zdefiniowany w `gateway`.
 */
interface GatewayInbound {
    fun receive(message: GatewayMessage): ProcessingResult
}
