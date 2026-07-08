package midomail.platform.android

import midomail.domain.adapter.Adapter

/**
 * Migawka stanu jednego zarejestrowanego adaptera do wyświetlenia na ekranie statusu
 * (ADR-0038-Ekran-Statusu-i-Konfiguracji-Android.md) — czytana bezpośrednio z żywych obiektów w
 * pamięci procesu, nie przez Adapter REST/CLI (ten ekran nie jest „UI" w rozumieniu ADR-0002 —
 * patrz ADR-0038, §To NIE jest UI).
 */
data class AdapterSnapshot(
    val adapterId: String,
    val healthy: Boolean,
    val messagesSent: Long,
    val messagesReceived: Long
)

/**
 * Czysta funkcja budująca migawki z listy zarejestrowanych adapterów — testowalna zwykłym testem
 * jednostkowym z ręcznie napisanymi atrapami [Adapter] (nie mock frameworka), zgodnie z
 * 50-Quality/50-Testy.md.
 */
fun buildAdapterSnapshots(adapters: List<Adapter>): List<AdapterSnapshot> = adapters.map { adapter ->
    AdapterSnapshot(
        adapterId = adapter.adapterId.value,
        healthy = adapter.health().healthy,
        messagesSent = adapter.metrics().messagesSent,
        messagesReceived = adapter.metrics().messagesReceived
    )
}
