package midomail.domain.adapter

import midomail.domain.gateway.GatewayInbound
import midomail.domain.message.Channel
import midomail.domain.message.GatewayMessage
import midomail.domain.port.AttachmentStore
import midomail.domain.port.EventPublisher
import midomail.domain.port.MessageStore

/**
 * Pełny kontrakt adaptera (SPEC-0010-Plugin-SDK-Contract.md, §Interfejs Adapter).
 *
 * Rozszerza [AdapterLifecycle] ([start]/[stop], zamrożone w Fazie 1, Iteracja 8b) — Registry
 * (10-Core/14-Registry-Adapterow.md) działa niezmieniona, przyjmuje dowolny [Adapter] tam, gdzie
 * oczekuje [AdapterLifecycle].
 *
 * [send] — kierunek wyjściowy: Gateway Engine wywołuje tę metodę, aby przekazać komunikat do
 * transportu. Adapter nie podejmuje decyzji routingowych — otrzymuje już wyznaczony komunikat.
 * Kierunek wejściowy (odbiór z transportu) nie jest metodą wywoływaną NA adapterze — adapter sam
 * inicjuje dostarczenie komunikatu do Gateway poprzez [GatewayInbound] otrzymany w [AdapterPorts]
 * (SPEC-0010, §Interfejs Adapter).
 */
interface Adapter : AdapterLifecycle {
    val adapterId: AdapterId
    val adapterVersion: String

    fun supportedChannels(): Set<Channel>
    fun supportedCapabilities(): Set<Capability>

    fun health(): HealthStatus
    fun metrics(): Metrics

    fun send(message: GatewayMessage)
}

/**
 * Porty przekazywane adapterowi przy tworzeniu (SPEC-0010-Plugin-SDK-Contract.md, §Porty
 * przekazywane adapterowi). [messageStore] jest przekazywany przede wszystkim do odczytu — jedyny
 * dozwolony zapis to wąski, jawnie udokumentowany wyjątek: rejestracja przez adapter identyfikatora
 * przydzielonego WYSŁANEJ przez niego samego wiadomości, żeby przyszła odpowiedź mogła się do niej
 * odwołać (ADR-0015-Rejestracja-Wyslanego-Message-Id.md — np. `EmailAdapter` rejestruje rzeczywisty
 * nagłówek `Message-ID` przydzielony przez serwer SMTP). Adapter nigdy nie czyta z Message Store w
 * celu podjęcia decyzji o wysyłce — czytanie służy wyłącznie wątkowaniu przychodzących odpowiedzi.
 *
 * [attachmentStore] dodany w Fazie 2 (Iteracja 2.6) — amendment do pierwotnie zamrożonego
 * kontraktu (Iteracja 2.1), analogicznie do ADR-0012 rozszerzającego `Channel` w Fazie 1.
 *
 * [eventPublisher] dodany w Fazie 3 (Iteracja 3.6) — amendment analogiczny do `attachmentStore`.
 * Umożliwia adapterowi publikację właściwych zdarzeń domenowych (np. obcięcie treści SMS,
 * ADR-0009-Obciecie-Tresci-SMS.md), tym samym mechanizmem co `Registry.AdapterStateChanged`
 * (10-Core/15-Event-Bus.md; SPEC-0003-Event-Model.md).
 */
data class AdapterPorts(
    val gatewayInbound: GatewayInbound,
    val messageStore: MessageStore,
    val logger: Logger,
    val healthReporter: HealthReporter,
    val attachmentStore: AttachmentStore,
    val eventPublisher: EventPublisher
)

/**
 * Marker — konkretny kształt konfiguracji należy do każdego adaptera z osobna
 * (SPEC-0010-Plugin-SDK-Contract.md, §Konfiguracja adaptera; SPEC-0005-Configuration-Model.md,
 * sekcja Adapters).
 */
interface AdapterConfiguration

/**
 * Jedyne miejsce tworzenia instancji adaptera (SPEC-0010-Plugin-SDK-Contract.md, §Mechanizm DI)
 * — wszystkie zależności przekazywane jako parametry, adapter nie sięga po nie samodzielnie.
 */
interface AdapterFactory {
    fun create(configuration: AdapterConfiguration, ports: AdapterPorts): Adapter
}
