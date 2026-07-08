package midomail.adapter.gsm

import android.content.Context
import midomail.domain.adapter.Adapter
import midomail.domain.adapter.AdapterConfiguration
import midomail.domain.adapter.AdapterFactory
import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.AdapterPorts

/**
 * Jedyne miejsce tworzenia instancji [GsmAdapter] (SPEC-0010-Plugin-SDK-Contract.md, §Mechanizm DI)
 * — wszystkie zależności przekazywane jako parametry [create].
 *
 * [mmsTransport] przekazywany z zewnątrz (nie konstruowany tutaj) — konkretna implementacja
 * ([midomail.platform.android.AndroidMmsTransport]) żyje w `:platform-android`, który zależy od
 * `:adapter-gsm`, nie odwrotnie; [context] jest wystarczający do zbudowania [SmsSender] bezpośrednio
 * (nie wymaga osobnego portu, w przeciwieństwie do MMS — Iteracja 3.7/3.10).
 */
class GsmAdapterFactory(
    private val adapterId: AdapterId,
    private val context: Context,
    private val mmsTransport: MmsTransport
) : AdapterFactory {

    override fun create(configuration: AdapterConfiguration, ports: AdapterPorts): Adapter {
        check(configuration is GsmAdapterConfiguration) {
            "GsmAdapterFactory wymaga GsmAdapterConfiguration, otrzymano: ${configuration::class}"
        }
        return GsmAdapter(
            adapterId = adapterId,
            adapterVersion = "1.0",
            smsTransport = SmsSender(context),
            mmsSender = MmsSender(mmsTransport),
            smsMapper = SmsMessageMapper(configuration.forwardToAddress, ports.messageStore),
            mmsMapper = MmsMessageMapper(ports.attachmentStore, configuration.forwardToAddress),
            truncator = SmsContentTruncator(configuration.maxSmsSegments, ports.eventPublisher),
            ports = ports
        )
    }
}
