package midomail.adapter.websocket

import midomail.domain.adapter.AdapterConfiguration

/**
 * Konfiguracja adaptera WebSocket (20-Adapters/24-Adapter-WebSocket.md;
 * SPEC-0026-WebSocket-Adapter-Contract.md, §3; SPEC-0005-Configuration-Model.md, sekcja Adapters).
 *
 * Gateway łączy się WYCHODZĄCO do [url] (klient, nie serwer — SPEC-0026 §1).
 */
data class WebSocketAdapterConfiguration(
    val url: String,
    val reconnectPolicy: ReconnectPolicy,
    val heartbeatIntervalMillis: Long
) : AdapterConfiguration

/**
 * Polityka ponownego łączenia po utracie połączenia (SPEC-0026 §3). Wyczerpanie
 * [maxAttempts] pozostawia adapter w stanie niezdrowym do czasu ręcznego `stop()`/`start()`
 * (np. przez restart adaptera w Admin API).
 */
data class ReconnectPolicy(
    val maxAttempts: Int,
    val backoffMillis: Long
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts nie może być ujemny" }
        require(backoffMillis > 0) { "backoffMillis musi być dodatni" }
    }
}
