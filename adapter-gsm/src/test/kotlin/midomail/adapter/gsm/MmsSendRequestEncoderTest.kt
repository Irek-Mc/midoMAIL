package midomail.adapter.gsm

import kotlin.test.Test
import kotlin.test.assertEquals

class MmsSendRequestEncoderTest {

    @Test
    fun `an encoded PDU can be decoded back by MmsRetrieveConfParser (round-trip)`() {
        val parts = listOf(
            MmsPart(contentType = "text/plain", fileName = null, bytes = "hello".toByteArray()),
            MmsPart(contentType = "image/jpeg", fileName = null, bytes = byteArrayOf(1, 2, 3, 4))
        )

        val pdu = MmsSendRequestEncoder.encode(transactionId = "TX-1", to = "+48123456789", parts = parts)
        val decodedParts = MmsRetrieveConfParser.parse(pdu)

        assertEquals(2, decodedParts.size)
        assertEquals("text/plain", decodedParts[0].contentType)
        assertEquals("hello", String(decodedParts[0].bytes, Charsets.US_ASCII))
        assertEquals("image/jpeg", decodedParts[1].contentType)
        assertEquals(listOf<Byte>(1, 2, 3, 4), decodedParts[1].bytes.toList())
    }

    @Test
    fun `a non-well-known content type is round-tripped as a text-string`() {
        val parts = listOf(MmsPart(contentType = "application/smil", fileName = null, bytes = "<smil/>".toByteArray()))

        val pdu = MmsSendRequestEncoder.encode(transactionId = "TX-2", to = "+48123456789", parts = parts)
        val decodedParts = MmsRetrieveConfParser.parse(pdu)

        assertEquals("application/smil", decodedParts.single().contentType)
    }

    @Test
    fun `many parts produce a correctly counted multipart body`() {
        val parts = (1..5).map { MmsPart(contentType = "text/plain", fileName = null, bytes = "part-$it".toByteArray()) }

        val pdu = MmsSendRequestEncoder.encode(transactionId = "TX-3", to = "+48123456789", parts = parts)
        val decodedParts = MmsRetrieveConfParser.parse(pdu)

        assertEquals(5, decodedParts.size)
        assertEquals("part-3", String(decodedParts[2].bytes, Charsets.US_ASCII))
    }

    /**
     * Regresja znaleziona na rzeczywistym urządzeniu (Iteracja 3.10): pole `To` musi używać pełnej
     * formy `Encoded-string-value` (`Value-length` + `Charset` + `Text-string`), nie samego
     * `Text-string` — inaczej Android odrzucał cały PDU (`updateDestinationAddress: can't parse
     * input PDU`) i wiadomość nie docierała, mimo że HTTP POST do MMSC technicznie się powodził.
     */
    @Test
    fun `regresja - the To field uses the full Value-length plus Charset plus Text-string form`() {
        val pdu = MmsSendRequestEncoder.encode(
            transactionId = "TX-4",
            to = "+48123456789",
            parts = listOf(MmsPart(contentType = "text/plain", fileName = null, bytes = "x".toByteArray()))
        )

        val toFieldIndex = pdu.indexOfFirst { it == 0x97.toByte() }
        assertEquals(true, toFieldIndex >= 0, "Pole To (0x97) powinno występować w PDU")

        val expectedAddress = "+48123456789/TYPE=PLMN"
        val valueLength = pdu[toFieldIndex + 1].toInt() and 0xFF
        val charsetByte = pdu[toFieldIndex + 2].toInt() and 0xFF
        val addressBytes = pdu.copyOfRange(toFieldIndex + 3, toFieldIndex + 3 + expectedAddress.length)

        assertEquals(expectedAddress.length + 2, valueLength) // +1 charset byte, +1 null terminator
        assertEquals(0x83, charsetByte) // US-ASCII (0x03) jako Short-Integer
        assertEquals(expectedAddress, String(addressBytes, Charsets.US_ASCII))
        assertEquals(0x00.toByte(), pdu[toFieldIndex + 3 + expectedAddress.length])
    }

    @Test
    fun `regresja - a From field with Insert-address-token is present so the carrier fills in the sender`() {
        val pdu = MmsSendRequestEncoder.encode(
            transactionId = "TX-5",
            to = "+48123456789",
            parts = listOf(MmsPart(contentType = "text/plain", fileName = null, bytes = "x".toByteArray()))
        )

        val fromFieldIndex = pdu.indexOfFirst { it == 0x89.toByte() }
        assertEquals(true, fromFieldIndex >= 0, "Pole From (0x89) powinno występować w PDU")
        assertEquals(1, pdu[fromFieldIndex + 1].toInt()) // Value-length = 1
        assertEquals(0x81, pdu[fromFieldIndex + 2].toInt() and 0xFF) // Insert-address-token
    }
}
