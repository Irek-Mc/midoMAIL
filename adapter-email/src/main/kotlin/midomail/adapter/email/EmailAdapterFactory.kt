package midomail.adapter.email

import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterConfiguration
import midomail.domain.adapter.AdapterFactory
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts
import midomail.domain.port.SchedulerProvider

/**
 * Jedyne miejsce tworzenia instancji [EmailAdapter] (SPEC-0010-Plugin-SDK-Contract.md,
 * §Mechanizm DI) — wszystkie zależności przekazywane jako parametry [create].
 */
class EmailAdapterFactory(
    private val adapterId: AdapterId,
    private val schedulerProvider: SchedulerProvider,
    private val fromDisplayName: String = "midoMAIL Gateway"
) : AdapterFactory {

    override fun create(configuration: AdapterConfiguration, ports: AdapterPorts): Adapter {
        check(configuration is EmailAdapterConfiguration) {
            "EmailAdapterFactory wymaga EmailAdapterConfiguration, otrzymano: ${configuration::class}"
        }
        val mapper = EmailMessageMapper(ports.attachmentStore, fromDisplayName)
        return EmailAdapter(
            adapterId = adapterId,
            adapterVersion = "1.0",
            smtpSender = SmtpSender(configuration.smtp),
            imapReceiver = ImapReceiver(configuration.imap),
            mapper = mapper,
            ports = ports,
            schedulerProvider = schedulerProvider,
            pollIntervalMillis = configuration.pollIntervalMillis
        )
    }
}
