package midomail.adapter.rest

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import midomail.domain.administration.AdminAuditRecorder
import midomail.domain.administration.AdminAuthenticator
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Serwer HTTP administracyjny (ADR-0023-Serwer-HTTP.md) — `com.sun.net.httpserver.HttpServer`,
 * kontynuacja filozofii „wbudowane w JDK" (ADR-0017), teraz jako serwer produkcyjny.
 *
 * Uwierzytelnienie ([AdminAuthenticator], ADR-0019) wymuszane przed dyspozycją do KAŻDEGO
 * zarejestrowanego handlera — nagłówek `X-API-Key`. Handler odpowiada za pełny cykl życia
 * `HttpExchange` (wysłanie odpowiedzi i zamknięcie) — router zamyka wymianę samodzielnie
 * wyłącznie na ścieżkach 401/404 (gdzie żaden handler nie jest wywoływany).
 *
 * [auditRecorder] (Iteracja 5.16, SPEC-0024 §Uwierzytelnianie i audyt) — wywoływany dla KAŻDEGO
 * błędu uwierzytelnienia (dowolna metoda) oraz dla każdej pomyślnie zdyspozycjonowanej metody
 * ZAPISU (`POST`/`DELETE`/`PUT`/`PATCH`, nie `GET` — decyzja SPEC-0024 poz. 10: odczyty
 * nieaudytowane domyślnie).
 *
 * **CORS** (Iteracja 6.16, ADR-0033, SA-13) — nagłówek `Access-Control-Allow-Origin: *` na KAŻDĄ
 * odpowiedź (w tym błędy) + obsługa `OPTIONS` (preflight) PRZED uwierzytelnieniem (przeglądarki
 * nie dołączają nagłówka klucza API do żądań preflight). Ograniczone do `*` — jawnie
 * udokumentowane uproszczenie profilu deweloperskiego/jednego operatora (ADR-0033), nie
 * produkcyjny model wieloserwisowy z listą dozwolonych originów.
 */
class AdminHttpServer(
    port: Int,
    private val authenticator: AdminAuthenticator,
    private val auditRecorder: AdminAuditRecorder = AdminAuditRecorder { _, _ -> }
) {

    private data class RouteKey(val method: String, val path: String)

    private val httpServer: HttpServer = HttpServer.create(InetSocketAddress(port), 0)
    private val routes = ConcurrentHashMap<RouteKey, (HttpExchange) -> Unit>()

    init {
        httpServer.createContext("/") { exchange -> dispatch(exchange) }
    }

    fun route(method: String, path: String, handler: (HttpExchange) -> Unit) {
        routes[RouteKey(method, path)] = handler
    }

    fun start() {
        httpServer.start()
    }

    fun stop() {
        httpServer.stop(0)
    }

    fun port(): Int = httpServer.address.port

    private fun dispatch(exchange: HttpExchange) {
        if (exchange.requestMethod == "OPTIONS") {
            respondPreflight(exchange)
            return
        }

        val operation = "${exchange.requestMethod} ${exchange.requestURI.path}${exchange.requestURI.query?.let { "?$it" } ?: ""}"
        val providedKey = exchange.requestHeaders.getFirst(API_KEY_HEADER) ?: ""
        if (!authenticator.authenticate(providedKey)) {
            auditRecorder.record(operation, authenticated = false)
            respond(exchange, 401, "Unauthorized")
            return
        }

        val handler = routes[RouteKey(exchange.requestMethod, exchange.requestURI.path)]
        if (handler == null) {
            respond(exchange, 404, "Not Found")
            return
        }
        if (exchange.requestMethod != "GET") {
            auditRecorder.record(operation, authenticated = true)
        }
        handler(exchange)
    }

    private fun respondPreflight(exchange: HttpExchange) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, PATCH, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, $API_KEY_HEADER")
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
    }

    companion object {
        const val API_KEY_HEADER = "X-API-Key"

        fun respond(exchange: HttpExchange, statusCode: Int, body: String) {
            exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
