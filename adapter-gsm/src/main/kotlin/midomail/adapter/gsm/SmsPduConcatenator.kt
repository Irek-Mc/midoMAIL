package midomail.adapter.gsm

/** Jeden segment PDU wyodrębniony z `android.telephony.SmsMessage` (surowe pola, nie obiekt Android). */
data class SmsSegment(val sender: String, val body: String, val timestampMillis: Long)

/**
 * Scalenie segmentów wieloczęściowego SMS w jedną logiczną wiadomość — Android dostarcza WSZYSTKIE
 * części długiego SMS w jednej transmisji `SMS_DELIVER_ACTION`
 * (`Telephony.Sms.Intents.getMessagesFromIntent`), każda ze wspólnym nadawcą/znacznikiem czasu,
 * ale osobną częścią treści do połączenia w kolejności.
 *
 * Czysta funkcja Kotlin, testowalna bez urządzenia (50-Quality/50-Testy.md, §5) — w przeciwieństwie
 * do samego wyodrębnienia [SmsSegment] z `android.telephony.SmsMessage`, które wymaga rzeczywistego
 * PDU i jest weryfikowane wyłącznie na urządzeniu ([SmsDeliverReceiver]).
 */
object SmsPduConcatenator {
    fun concatenate(segments: List<SmsSegment>): SmsSegment {
        require(segments.isNotEmpty()) { "Brak segmentów do scalenia" }
        val first = segments.first()
        return SmsSegment(
            sender = first.sender,
            body = segments.joinToString(separator = "") { it.body },
            timestampMillis = first.timestampMillis
        )
    }
}
