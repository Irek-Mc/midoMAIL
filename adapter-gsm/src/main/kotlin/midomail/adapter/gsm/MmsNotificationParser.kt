package midomail.adapter.gsm

/**
 * Wynik parsowania powiadomienia MMS (M-Notification.ind) dostarczonego przez `WAP_PUSH_DELIVER_ACTION`.
 * [contentLocationUrl] wskazuje, skąd pobrać rzeczywistą treść (`SmsManager.downloadMultimediaMessage`,
 * Iteracja 3.10).
 */
data class MmsNotification(val transactionId: String, val contentLocationUrl: String)

/**
 * Parser powiadomienia M-Notification.ind (OMA-WAP-MMS-ENC; kodowanie WSP, OMA-WAP-230-WSP).
 * Android nie udostępnia publicznego API do parsowania surowego PDU MMS
 * (`com.google.android.mms.pdu` to klasy wewnętrzne frameworka, niedostępne dla aplikacji) —
 * ten parser wyodrębnia wyłącznie pola potrzebne do pobrania treści (Transaction-Id,
 * Content-Location), nie jest ogólnym dekoderem wszystkich pól nagłówkowych MMS.
 *
 * Reguły kodowania (zweryfikowane względem działającej implementacji referencyjnej,
 * python-messaging `mms_pdu.py`/`wsp_pdu.py`):
 * - kody pól nagłówkowych mają ustawiony najstarszy bit (np. Message-Type=0x8C, Transaction-Id=0x98,
 *   MMS-Version=0x8D, Content-Location=0x83),
 * - Message-Type/MMS-Version: pojedynczy surowy bajt (nie maskowany),
 * - Text-String: opcjonalny bajt cudzysłowu (0x7F) do pominięcia, potem bajty do terminatora 0x00,
 * - `X-Mms-Message-Type` dla m-notification-ind = 0x82; Message-Type, Transaction-Id, MMS-Version
 *   MUSZĄ wystąpić na początku PDU w tej kolejności (wymóg specyfikacji).
 *
 * Pola po tych trzech nagłówkach nie są w pełni dekodowane (Message-Class/Message-Size/Expiry/From
 * mają zróżnicowane, złożone kodowanie spoza zakresu tego parsera) — Content-Location wyszukiwany
 * jest przez skan bajtu pola (0x83) z walidacją, że zdekodowana wartość wygląda jak URL (`http`),
 * co ogranicza ryzyko fałszywego trafienia na bajt 0x83 wewnątrz wartości innego pola.
 */
object MmsNotificationParser {

    fun parse(pdu: ByteArray): MmsNotification? {
        val cursor = Cursor(pdu)

        if (!cursor.consumeIfEquals(MESSAGE_TYPE_FIELD)) return null
        if (!cursor.consumeIfEquals(MESSAGE_TYPE_NOTIFICATION_IND)) return null

        if (!cursor.consumeIfEquals(TRANSACTION_ID_FIELD)) return null
        val transactionId = cursor.readTextString() ?: return null

        // MMS-Version: pojedynczy surowy bajt, wartość nieużywana przez ten parser.
        if (!cursor.consumeIfEquals(MMS_VERSION_FIELD)) return null
        cursor.skip(1)

        val contentLocationUrl = findContentLocation(pdu, cursor.position) ?: return null
        return MmsNotification(transactionId, contentLocationUrl)
    }

    private fun findContentLocation(pdu: ByteArray, fromIndex: Int): String? {
        for (index in fromIndex until pdu.size) {
            if (pdu[index] != CONTENT_LOCATION_FIELD) continue
            val candidate = Cursor(pdu, index + 1).readTextString()
            if (candidate != null && (candidate.startsWith("http://") || candidate.startsWith("https://"))) {
                return candidate
            }
        }
        return null
    }

    private class Cursor(private val bytes: ByteArray, start: Int = 0) {
        var position: Int = start
            private set

        fun consumeIfEquals(expected: Byte): Boolean {
            if (position >= bytes.size || bytes[position] != expected) return false
            position++
            return true
        }

        fun skip(count: Int) {
            position += count
        }

        fun readTextString(): String? {
            if (position >= bytes.size) return null
            if (bytes[position] == QUOTE_BYTE) {
                position++
            }
            val start = position
            while (position < bytes.size && bytes[position] != NULL_TERMINATOR) {
                position++
            }
            if (position >= bytes.size) return null
            val text = String(bytes, start, position - start, Charsets.US_ASCII)
            position++
            return text
        }
    }

    private const val MESSAGE_TYPE_FIELD: Byte = 0x8C.toByte()
    private const val MESSAGE_TYPE_NOTIFICATION_IND: Byte = 0x82.toByte()
    private const val TRANSACTION_ID_FIELD: Byte = 0x98.toByte()
    private const val MMS_VERSION_FIELD: Byte = 0x8D.toByte()
    private const val CONTENT_LOCATION_FIELD: Byte = 0x83.toByte()
    private const val QUOTE_BYTE: Byte = 0x7F
    private const val NULL_TERMINATOR: Byte = 0x00
}
