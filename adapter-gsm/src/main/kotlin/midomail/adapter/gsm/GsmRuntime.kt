package midomail.adapter.gsm

import midomail.domain.gateway.GatewayInbound

/**
 * Punkt wiązania między komponentami tworzonymi przez system Android bez wstrzykiwania zależności
 * (`BroadcastReceiver`/`Service` zadeklarowane w manifeście — instancjonowane bezargumentowo przez
 * OS, nie przez [midomail.domain.adapter.AdapterFactory]) a resztą Gateway.
 *
 * Wypełniany przez `GsmAdapter.start()` (Iteracja 3.11) — dopóki adapter nie wystartuje, odbiornik
 * bezpiecznie ignoruje przychodzące transmisje (`null`), nie zgłasza wyjątku.
 */
object GsmRuntime {
    @Volatile
    var gatewayInbound: GatewayInbound? = null

    @Volatile
    var mapper: SmsMessageMapper? = null

    @Volatile
    var mmsMapper: MmsMessageMapper? = null
}
