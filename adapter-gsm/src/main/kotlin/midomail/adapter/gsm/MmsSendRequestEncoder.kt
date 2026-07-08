package midomail.adapter.gsm

import java.io.ByteArrayOutputStream

/**
 * Enkoder PDU M-Send.req (OMA-WAP-MMS-ENC) — odwrotność [MmsRetrieveConfParser]. `SmsManager.sendMultimediaMessage`
 * (Android) wymaga gotowego, zakodowanego PDU zapisanego pod `content://` Uri — w przeciwieństwie do
 * wysyłki SMS, system nie przyjmuje samej treści, tylko już zbudowane PDU.
 *
 * Kodowanie zweryfikowane względem tych samych źródeł co parsery (AOSP `PduContentTypes.java`,
 * referencyjna implementacja `python-messaging`) dla Content-Type oraz dodatkowo względem
 * `PduComposer.java` (oficjalny enkoder AOSP) dla pól `To`/`From` — **Błąd znaleziony i naprawiony
 * podczas weryfikacji na rzeczywistym urządzeniu**: pole `To` musi używać pełnej formy
 * `Encoded-string-value` (`Value-length` + `Charset` + `Text-string`), nie samego `Text-string`
 * (technicznie dopuszczalna alternatywa wg specyfikacji WSP, ale własny `PduParser` Androida jej
 * nie akceptuje — powodowało to całkowite niepowodzenie parsowania PDU po stronie systemu,
 * `MmsService: updateDestinationAddress: can't parse input PDU`, mimo że HTTP POST do MMSC operatora
 * technicznie się powodził; wiadomość nie docierała poprawnie do adresata).
 */
object MmsSendRequestEncoder {

    fun encode(transactionId: String, to: String, parts: List<MmsPart>): ByteArray {
        val output = ByteArrayOutputStream()
        // Message-Type: bajt m-send-req (0x80) jest już kompletną wartością na drucie (nie
        // semantyczną wartością 0-127 do złożenia OR-em z 0x80, w przeciwieństwie do MMS-Version).
        output.write(MESSAGE_TYPE_FIELD.toInt())
        output.write(MESSAGE_TYPE_SEND_REQ.toInt())
        writeTextString(output, TRANSACTION_ID_FIELD, transactionId)
        writeShortInteger(output, MMS_VERSION_FIELD, MMS_VERSION_1_0)
        writeFromInsertAddressToken(output)
        writeTo(output, to)
        writeContentTypeAndBody(output, parts)
        return output.toByteArray()
    }

    /** `From`: Insert-address-token (0x81) — carrier/MMSC wypełnia nadawcę na podstawie karty SIM. */
    private fun writeFromInsertAddressToken(output: ByteArrayOutputStream) {
        output.write(FROM_FIELD.toInt())
        writeValueLength(output, 1)
        output.write(INSERT_ADDRESS_TOKEN.toInt())
    }

    private fun writeTo(output: ByteArrayOutputStream, to: String) {
        output.write(TO_FIELD.toInt())
        writeEncodedStringValue(output, "$to/TYPE=PLMN")
    }

    /** `Encoded-string-value = Value-length Char-set Text-string` — pełna forma wymagana przez PduParser Androida. */
    private fun writeEncodedStringValue(output: ByteArrayOutputStream, text: String) {
        val body = ByteArrayOutputStream()
        body.write(CHARSET_US_ASCII or 0x80)
        writeAsciiWithNullTerminator(body, text)
        val bodyBytes = body.toByteArray()
        writeValueLength(output, bodyBytes.size)
        output.write(bodyBytes)
    }

    private fun writeContentTypeAndBody(output: ByteArrayOutputStream, parts: List<MmsPart>) {
        output.write(CONTENT_TYPE_FIELD.toInt())
        // Content-Type: multipart/related, forma Constrained (Short-Integer), Value-length = 1.
        writeValueLength(output, 1)
        output.write((MULTIPART_RELATED_CODE or 0x80))

        val body = ByteArrayOutputStream()
        writeUintVar(body, parts.size)
        parts.forEach { part -> writePart(body, part) }
        output.write(body.toByteArray())
    }

    private fun writePart(output: ByteArrayOutputStream, part: MmsPart) {
        val headerBytes = encodePartContentType(part.contentType)
        writeUintVar(output, headerBytes.size)
        writeUintVar(output, part.bytes.size)
        output.write(headerBytes)
        output.write(part.bytes)
    }

    private fun encodePartContentType(contentType: String): ByteArray {
        val wellKnownCode = WELL_KNOWN_CONTENT_TYPE_CODES[contentType]
        val output = ByteArrayOutputStream()
        if (wellKnownCode != null) {
            output.write(wellKnownCode or 0x80)
        } else {
            writeAsciiWithNullTerminator(output, contentType)
        }
        return output.toByteArray()
    }

    private fun writeShortInteger(output: ByteArrayOutputStream, fieldCode: Byte, value: Int) {
        output.write(fieldCode.toInt())
        output.write(value or 0x80)
    }

    private fun writeTextString(output: ByteArrayOutputStream, fieldCode: Byte, text: String) {
        output.write(fieldCode.toInt())
        writeAsciiWithNullTerminator(output, text)
    }

    private fun writeAsciiWithNullTerminator(output: ByteArrayOutputStream, text: String) {
        output.write(text.toByteArray(Charsets.US_ASCII))
        output.write(0x00)
    }

    private fun writeValueLength(output: ByteArrayOutputStream, length: Int) {
        if (length in 0..30) {
            output.write(length)
        } else {
            output.write(0x1F)
            writeUintVar(output, length)
        }
    }

    private fun writeUintVar(output: ByteArrayOutputStream, value: Int) {
        var remaining = value
        val septets = mutableListOf<Int>()
        do {
            septets.add(0, remaining and 0x7F)
            remaining = remaining shr 7
        } while (remaining > 0)
        septets.forEachIndexed { index, septet ->
            val isLast = index == septets.size - 1
            output.write(if (isLast) septet else (septet or 0x80))
        }
    }

    private const val MESSAGE_TYPE_FIELD: Byte = 0x8C.toByte()
    private const val MESSAGE_TYPE_SEND_REQ: Byte = 0x80.toByte()
    private const val TRANSACTION_ID_FIELD: Byte = 0x98.toByte()
    private const val MMS_VERSION_FIELD: Byte = 0x8D.toByte()
    private const val MMS_VERSION_1_0 = 0x10 // |0x80 = 0x90
    private const val FROM_FIELD: Byte = 0x89.toByte()
    private const val INSERT_ADDRESS_TOKEN: Byte = 0x81.toByte()
    private const val CHARSET_US_ASCII = 0x03
    private const val TO_FIELD: Byte = 0x97.toByte()
    private const val CONTENT_TYPE_FIELD: Byte = 0x84.toByte()
    private const val MULTIPART_RELATED_CODE = 0x33

    private val WELL_KNOWN_CONTENT_TYPE_CODES: Map<String, Int> = mapOf(
        "text/plain" to 0x03,
        "image/gif" to 0x1D,
        "image/jpeg" to 0x1E,
        "image/png" to 0x20
    )
}
