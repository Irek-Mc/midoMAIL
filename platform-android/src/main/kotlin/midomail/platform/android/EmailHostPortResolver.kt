package midomail.platform.android

import midomail.domain.port.SecretStore

/**
 * Rozwiązuje host/port SMTP/IMAP z [SecretStore], z wartością domyślną Gmaila jeśli klucz nie
 * został jeszcze ustawiony (ADR-0038-Ekran-Statusu-i-Konfiguracji-Android.md) — identyczny wzorzec
 * jak `createEmailAdapterIfConfigured` w `platform-jvm/src/main/kotlin/midomail/platform/jvm/Main.kt`
 * (Faza 7, Iteracja 7.8). Czysta funkcja, niezależna od API Android — testowalna zwykłym testem
 * jednostkowym (50-Quality/50-Testy.md, §5).
 */
data class HostPort(val host: String, val port: Int)

fun resolveHostPort(
    secretStore: SecretStore,
    hostKey: String,
    portKey: String,
    defaultHost: String,
    defaultPort: Int
): HostPort = HostPort(
    host = secretStore.read(hostKey) ?: defaultHost,
    port = secretStore.read(portKey)?.toIntOrNull() ?: defaultPort
)
