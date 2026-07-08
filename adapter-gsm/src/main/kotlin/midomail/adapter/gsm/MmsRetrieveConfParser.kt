package midomail.adapter.gsm

/**
 * Parser pobranej treści MMS (M-Retrieve.conf, OMA-WAP-MMS-ENC) — struktura multipart/related:
 * liczba części (Uintvar), a potem dla każdej części: długość nagłówków (Uintvar), długość danych
 * (Uintvar), nagłówki (zawierające Content-Type), dane.
 *
 * Android nie udostępnia publicznego API do parsowania tego PDU (`com.google.android.mms.pdu` to
 * klasy wewnętrzne frameworka) — ten parser jest samodzielną implementacją, zweryfikowaną względem
 * oficjalnego źródła AOSP (`PduContentTypes.java`, `android.googlesource.com/platform/frameworks/base`)
 * dla tabeli dobrze znanych typów MIME oraz działającej implementacji referencyjnej
 * (python-messaging `mms_pdu.py`/`wsp_pdu.py`) dla reguł kodowania Uintvar/Value-Length/Text-String.
 *
 * Odnajduje początek sekcji multipart przez skan bajtu pola Content-Type (0x84 — nagłówek
 * zewnętrznego PDU, nie mylić z Content-Type per-część, który jest formą bez prefiksu pola), z tych
 * samych powodów pragmatycznych co [MmsNotificationParser] (pełne zdekodowanie WSZYSTKICH pól
 * nagłówkowych M-Retrieve.conf — From/Subject/Date/itd. — ma zróżnicowane, złożone kodowanie poza
 * zakresem tego parsera).
 */
object MmsRetrieveConfParser {

    fun parse(pdu: ByteArray): List<MmsPart> {
        val multipartStart = findMultipartBodyStart(pdu) ?: return emptyList()
        return decodeMultipartEntries(pdu, multipartStart)
    }

    /**
     * Pole `From` (kod pola 0x89 = 0x09 | bit wysoki) — `Value-length`, potem
     * Address-present-token (0x80, adres następuje) albo Insert-address-token (0x81, adres
     * nieznany/nie wstawiony). Pełna forma `Encoded-string-value` dopuszcza opcjonalny prefiks
     * zestawu znaków (Value-length + Charset) przed właściwym tekstem — zamiast pełnego
     * dekodowania tego prefiksu, wyszukiwany jest najdłuższy ciąg drukowalnych znaków ASCII
     * (cyfry/litery/`+`/`.`/`@`/`-`) w obrębie przydzielonych `Value-length` bajtów, co odczytuje
     * właściwy adres niezależnie od tego, czy prefiks zestawu znaków jest obecny.
     */
    fun parseSender(pdu: ByteArray): String? {
        for (index in pdu.indices) {
            if (pdu[index] != FROM_FIELD) continue
            val cursor = Cursor(pdu, index + 1)
            val length = cursor.readValueLength() ?: continue
            val valueStart = cursor.position
            val valueEnd = (valueStart + length).coerceAtMost(pdu.size)
            if (valueStart >= valueEnd) continue
            if (pdu[valueStart] == INSERT_ADDRESS_TOKEN) continue

            val address = longestPrintableRun(pdu, valueStart + 1, valueEnd)
            if (address != null) return address
        }
        return null
    }

    private fun longestPrintableRun(pdu: ByteArray, from: Int, to: Int): String? {
        var bestStart = -1
        var bestLength = 0
        var currentStart = -1
        var index = from
        while (index <= to) {
            val isPrintable = index < to && isAddressChar(pdu[index])
            if (isPrintable) {
                if (currentStart == -1) currentStart = index
            } else {
                if (currentStart != -1 && index - currentStart > bestLength) {
                    bestStart = currentStart
                    bestLength = index - currentStart
                }
                currentStart = -1
            }
            index++
        }
        if (bestLength == 0) return null
        return String(pdu, bestStart, bestLength, Charsets.US_ASCII)
    }

    private fun isAddressChar(byte: Byte): Boolean {
        val char = byte.toInt().toChar()
        return char.isLetterOrDigit() || char == '+' || char == '.' || char == '@' || char == '-'
    }

    /**
     * Nagłówek Content-Type (0x84) zewnętrznego PDU poprzedza bezpośrednio ciało multipart —
     * wartość samego Content-Type (np. "application/vnd.wap.multipart.related") jest pomijana,
     * interesuje nas wyłącznie miejsce, w którym zaczyna się `nEntries` (Uintvar).
     */
    private fun findMultipartBodyStart(pdu: ByteArray): Int? {
        for (index in pdu.indices) {
            if (pdu[index] != CONTENT_TYPE_FIELD) continue
            val cursor = Cursor(pdu, index + 1)
            val length = cursor.readValueLength() ?: continue
            val bodyStart = cursor.position + length
            if (bodyStart <= pdu.size) return bodyStart
        }
        return null
    }

    private fun decodeMultipartEntries(pdu: ByteArray, start: Int): List<MmsPart> {
        val cursor = Cursor(pdu, start)
        val entryCount = cursor.readUintVar() ?: return emptyList()

        val parts = mutableListOf<MmsPart>()
        repeat(entryCount) {
            val headersLength = cursor.readUintVar() ?: return parts
            val dataLength = cursor.readUintVar() ?: return parts
            val headersEnd = cursor.position + headersLength
            if (headersEnd > pdu.size) return parts

            val contentType = decodeEntryContentType(pdu, cursor.position, headersEnd)
            cursor.moveTo(headersEnd)

            val dataEnd = cursor.position + dataLength
            if (dataEnd > pdu.size) return parts
            val data = pdu.copyOfRange(cursor.position, dataEnd)
            cursor.moveTo(dataEnd)

            parts.add(MmsPart(contentType = contentType, fileName = null, bytes = data))
        }
        return parts
    }

    /** Content-Type-value per część: Short-Integer (dobrze znany typ) albo Text-String (rozszerzony). */
    /**
     * `Content-type-value = Constrained-media | Content-general-form`. Constrained-media to
     * Short-Integer (dobrze znany typ, najstarszy bit ustawiony) albo goły Text-String. Rzeczywiste
     * MMS w praktyce często używają Content-general-form (`Value-length Media-type Parameters` —
     * np. parametr `Name` niosący nazwę pliku) — bez tej formy parser odczytywał bajt
     * Value-length jako początek Text-String, dając zniekształconą wartość (znaleziony i
     * naprawiony błąd — Iteracja 3.10, weryfikacja na rzeczywistym urządzeniu). Rozróżnienie: bajt
     * Value-length (0–31) jest niedrukowalny, więc nie koliduje z faktycznym Text-String, który
     * zawsze zaczyna się drukowalnym znakiem ASCII (≥32).
     */
    private fun decodeEntryContentType(pdu: ByteArray, start: Int, end: Int): String {
        if (start >= end) return UNKNOWN_CONTENT_TYPE
        val first = pdu[start].toInt() and 0xFF
        return when {
            isHighBitSet(pdu[start]) -> WELL_KNOWN_CONTENT_TYPES[first and 0x7F] ?: UNKNOWN_CONTENT_TYPE
            first <= 31 -> decodeGeneralFormContentType(pdu, start, end)
            else -> Cursor(pdu, start).readTextString() ?: UNKNOWN_CONTENT_TYPE
        }
    }

    private fun decodeGeneralFormContentType(pdu: ByteArray, start: Int, end: Int): String {
        val cursor = Cursor(pdu, start)
        cursor.readValueLength() ?: return UNKNOWN_CONTENT_TYPE
        val mediaTypeStart = cursor.position
        if (mediaTypeStart >= end) return UNKNOWN_CONTENT_TYPE
        val mediaTypeByte = pdu[mediaTypeStart]
        return if (isHighBitSet(mediaTypeByte)) {
            WELL_KNOWN_CONTENT_TYPES[(mediaTypeByte.toInt() and 0x7F)] ?: UNKNOWN_CONTENT_TYPE
        } else {
            Cursor(pdu, mediaTypeStart).readTextString() ?: UNKNOWN_CONTENT_TYPE
        }
    }

    private fun isHighBitSet(byte: Byte): Boolean = (byte.toInt() and 0x80) != 0

    private class Cursor(private val bytes: ByteArray, start: Int) {
        var position: Int = start
            private set

        fun moveTo(newPosition: Int) {
            position = newPosition
        }

        fun skip(count: Int) {
            position += count
        }

        fun readUintVar(): Int? {
            var value = 0
            var readAny = false
            while (position < bytes.size) {
                val byte = bytes[position].toInt() and 0xFF
                position++
                value = (value shl 7) or (byte and 0x7F)
                readAny = true
                if (byte and 0x80 == 0) return value
            }
            return if (readAny) null else null
        }

        /** Value-length = Short-length (0..30) | Length-quote(31) + Uintvar. */
        fun readValueLength(): Int? {
            if (position >= bytes.size) return null
            val first = bytes[position].toInt() and 0xFF
            return if (first in 0..30) {
                position++
                first
            } else if (first == 31) {
                position++
                readUintVar()
            } else {
                null
            }
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

    private const val CONTENT_TYPE_FIELD: Byte = 0x84.toByte()
    private const val FROM_FIELD: Byte = 0x89.toByte()
    private const val INSERT_ADDRESS_TOKEN: Byte = 0x81.toByte()
    private const val QUOTE_BYTE: Byte = 0x7F
    private const val NULL_TERMINATOR: Byte = 0x00
    private const val UNKNOWN_CONTENT_TYPE = "application/octet-stream"

    // Tabela dobrze znanych typów MIME (WSP Content-Type) - podzbiór potwierdzony względem
    // android.googlesource.com/platform/frameworks/base, telephony/common/.../PduContentTypes.java.
    private val WELL_KNOWN_CONTENT_TYPES: Map<Int, String> = mapOf(
        0x02 to "text/html",
        0x03 to "text/plain",
        0x0B to "multipart/*",
        0x0C to "multipart/mixed",
        0x0D to "multipart/form-data",
        0x0F to "multipart/alternative",
        0x1C to "image/*",
        0x1D to "image/gif",
        0x1E to "image/jpeg",
        0x20 to "image/png",
        0x33 to "multipart/related",
        0x4F to "audio/*",
        0x50 to "video/*"
    )
}
