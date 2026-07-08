package midomail.adapter.gsm

/**
 * Port zapisu zakodowanego PDU M-Send.req pod współdzielonym `content://` Uri i wywołania
 * rzeczywistej wysyłki — realizacja zależy od `android.telephony.SmsManager`/`androidx.core.content.FileProvider`,
 * więc pozostaje w `:platform-android` ([midomail.platform.android.MmsSenderImpl] — nazwa
 * ilustracyjna, klasa dodana w tym samym pliku co odbiór MMS). Ten interfejs w `:adapter-gsm`
 * pozwala [MmsSendRequestEncoder] (czysty Kotlin) pozostać niezależnym od Androida — `send()`
 * jest cienkim, zależnym od platformy punktem styku, weryfikowanym wyłącznie na urządzeniu.
 */
interface MmsTransport {
    fun send(pdu: ByteArray, onResult: (SmsSendResult) -> Unit)
}

/** Łączy [MmsSendRequestEncoder] (kodowanie) z [MmsTransport] (rzeczywista wysyłka przez system). */
class MmsSender(private val transport: MmsTransport) {
    fun send(to: String, parts: List<MmsPart>, onResult: (SmsSendResult) -> Unit) {
        val transactionId = "midomail-${System.nanoTime()}"
        val pdu = MmsSendRequestEncoder.encode(transactionId, to, parts)
        transport.send(pdu, onResult)
    }
}
