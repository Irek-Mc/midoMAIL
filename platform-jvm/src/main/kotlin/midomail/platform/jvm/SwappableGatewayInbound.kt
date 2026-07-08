package midomail.platform.jvm

import midomail.domain.gateway.GatewayInbound
import midomail.domain.gateway.ProcessingResult
import midomail.domain.message.GatewayMessage

/**
 * Deleguje do aktualnego [delegate] — jedyny sposób, w jaki zmiany reguł routingu przez Admin API
 * (REST/CLI/UI) faktycznie wpływają na żywe przetwarzanie komunikatów, BEZ modyfikacji
 * `GatewayEngine`/`RoutingEngine` (ADR-0036, §Amendment: „propagacja zmian reguł do żywego
 * przetwarzania"). `GatewayEngine.routingEngine` jest polem `private val` — nie istnieje mechanizm
 * podmiany `RoutingEngine` w już skonstruowanym `GatewayEngine`. Ten wrapper implementuje
 * [GatewayInbound] (już istniejący interfejs, zero zmian w `:domain`) i jest przekazywany jako
 * `AdapterPorts.gatewayInbound` do wszystkich adapterów zamiast bezpośrednio `GatewayEngine` —
 * `Main.kt` podmienia [delegate] na świeżo zbudowany `GatewayEngine` (ta sama `ExactlyOnceEngine`/
 * `EventPublisher`/`GatewayOutbound`, nowy `RoutingEngine`) po wykryciu zmiany reguł.
 */
class SwappableGatewayInbound(@Volatile var delegate: GatewayInbound) : GatewayInbound {
    override fun receive(message: GatewayMessage): ProcessingResult = delegate.receive(message)
}
