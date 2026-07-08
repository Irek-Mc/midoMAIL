package midomail.ui.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Serwer plików statycznych (ADR-0033-Modul-UI-Web.md) — `com.sun.net.httpserver.HttpServer`,
 * ten sam duch co `AdminHttpServer` (`:adapter-rest`, Faza 5), ale inna odpowiedzialność:
 * serwowanie zasobów z classpath (`/static/`), nie routing API. Zero zależności na `:domain`.
 *
 * `/` mapuje na `index.html`. Nieznana ścieżka → 404. Typ zawartości zgadywany po rozszerzeniu
 * pliku (`.html`/`.css`/`.js`/domyślnie `application/octet-stream`).
 */
class StaticFileServer(port: Int) {

    private val httpServer: HttpServer = HttpServer.create(InetSocketAddress(port), 0)

    init {
        httpServer.createContext("/") { exchange -> serve(exchange) }
    }

    fun start() {
        httpServer.start()
    }

    fun stop() {
        httpServer.stop(0)
    }

    fun port(): Int = httpServer.address.port

    private fun serve(exchange: HttpExchange) {
        val requestedPath = exchange.requestURI.path.let { if (it == "/") "/index.html" else it }
        val resourcePath = "/static$requestedPath"
        val resource = javaClass.getResourceAsStream(resourcePath)
        if (resource == null) {
            respond(exchange, 404, "Not Found".toByteArray(Charsets.UTF_8), "text/plain")
            return
        }
        val bytes = resource.use { it.readBytes() }
        respond(exchange, 200, bytes, contentTypeFor(requestedPath))
    }

    private fun contentTypeFor(path: String): String = when {
        path.endsWith(".html") -> "text/html; charset=utf-8"
        path.endsWith(".css") -> "text/css; charset=utf-8"
        path.endsWith(".js") -> "application/javascript; charset=utf-8"
        path.endsWith(".json") -> "application/json; charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun respond(exchange: HttpExchange, statusCode: Int, body: ByteArray, contentType: String) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(statusCode, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
}

fun main() {
    val port = System.getenv("UI_WEB_PORT")?.toIntOrNull() ?: 8081
    val server = StaticFileServer(port)
    server.start()
    println("midoMAIL :ui-web uruchomiony na porcie ${server.port()}")
}
