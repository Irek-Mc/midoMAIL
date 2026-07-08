package midomail.adapter.gsm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MmsRetrieveConfParserTest {

    @Test
    fun `parses a text and an image part from a minimal multipart-related body`() {
        val pdu = intsToByteArray(
            0x84, 0x01, 0xB3, // Content-Type: multipart/related (short-integer 0x33)
            0x02, // nEntries = 2
            0x01, 0x05, 0x83, *"hello".toAsciiInts(), // entry 1: text/plain, "hello"
            0x01, 0x03, 0x9E, 0x01, 0x02, 0x03 // entry 2: image/jpeg, 3 raw bytes
        )

        val parts = MmsRetrieveConfParser.parse(pdu)

        assertEquals(2, parts.size)
        assertEquals("text/plain", parts[0].contentType)
        assertEquals("hello", String(parts[0].bytes, Charsets.US_ASCII))
        assertEquals("image/jpeg", parts[1].contentType)
        assertTrue(parts[1].bytes.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `a text-string content type is decoded as-is`() {
        val pdu = intsToByteArray(
            0x84, 0x01, 0xB3,
            0x01, // nEntries = 1
            *headerAndData(
                header = "application/smil".toAsciiInts() + intArrayOf(0x00),
                data = "<smil/>".toAsciiInts()
            )
        )

        val parts = MmsRetrieveConfParser.parse(pdu)

        assertEquals("application/smil", parts.single().contentType)
    }

    @Test
    fun `regresja - a general-form content type (value-length prefixed, e-g- with a Name parameter) is decoded without leading garbage`() {
        // Content-general-form = Value-length Media-type Parameters - znaleziony na rzeczywistym
        // urządzeniu (Iteracja 3.10): część obrazu MMS realnego operatora niosła Content-Type w tej
        // formie (parametr "Name" z nazwą pliku po typie), co bez tej obsługi dawało zniekształconą
        // wartość zaczynającą się od bajtu Value-length potraktowanego jako pierwszy znak tekstu.
        val mediaType = "image/jpeg".toAsciiInts() + intArrayOf(0x00)
        val nameParameter = intArrayOf(0x85, 0x0A) + "photo.jpg".toAsciiInts() + intArrayOf(0x00) // parametr Name (uproszczony)
        val generalFormContentType = intArrayOf(mediaType.size + nameParameter.size) + mediaType + nameParameter

        val pdu = intsToByteArray(
            0x84, 0x01, 0xB3,
            0x01,
            *headerAndData(header = generalFormContentType, data = intArrayOf(1, 2, 3))
        )

        val parts = MmsRetrieveConfParser.parse(pdu)

        assertEquals("image/jpeg", parts.single().contentType)
    }

    @Test
    fun `regresja - a general-form Text-String content type does not leak the value-length prefix`() {
        val mediaType = "application/smil".toAsciiInts() + intArrayOf(0x00)
        val generalFormContentType = intArrayOf(mediaType.size) + mediaType

        val pdu = intsToByteArray(
            0x84, 0x01, 0xB3,
            0x01,
            *headerAndData(header = generalFormContentType, data = "<smil/>".toAsciiInts())
        )

        val parts = MmsRetrieveConfParser.parse(pdu)

        assertEquals("application/smil", parts.single().contentType)
    }

    @Test
    fun `an unrecognised well-known content type code falls back to a generic type`() {
        val pdu = intsToByteArray(
            0x84, 0x01, 0xB3,
            0x01,
            0x01, 0x03, 0xFE, 0x01, 0x02, 0x03 // 0xFE & 0x7F = 0x7E, not in the known table
        )

        val parts = MmsRetrieveConfParser.parse(pdu)

        assertEquals("application/octet-stream", parts.single().contentType)
    }

    @Test
    fun `a PDU without a Content-Type field yields no parts`() {
        val pdu = intsToByteArray(0x8C, 0x84, 0x98, *"TX".toAsciiInts(), 0x00)

        assertTrue(MmsRetrieveConfParser.parse(pdu).isEmpty())
    }

    @Test
    fun `a truncated multipart body does not throw and returns whatever could be decoded`() {
        val pdu = intsToByteArray(
            0x84, 0x01, 0xB3,
            0x02, // claims 2 entries
            0x01, 0x05, 0x83, *"hello".toAsciiInts() // only 1 actually present, then PDU ends
        )

        val parts = MmsRetrieveConfParser.parse(pdu)

        assertEquals(1, parts.size)
        assertEquals("hello", String(parts[0].bytes, Charsets.US_ASCII))
    }

    @Test
    fun `parses the sender phone number from an Address-present From field`() {
        val address = "+48123456789"
        val pdu = intsToByteArray(
            0x89, 14, 0x80, *address.toAsciiInts(), 0x00 // From: Value-length=14, present, address, NUL
        )

        assertEquals(address, MmsRetrieveConfParser.parseSender(pdu))
    }

    @Test
    fun `an Insert-address-token From field yields no sender`() {
        val pdu = intsToByteArray(0x89, 1, 0x81) // From: Value-length=1, Insert-address-token

        assertEquals(null, MmsRetrieveConfParser.parseSender(pdu))
    }

    @Test
    fun `a PDU without a From field yields no sender`() {
        val pdu = intsToByteArray(0x8C, 0x84)

        assertEquals(null, MmsRetrieveConfParser.parseSender(pdu))
    }

    private fun headerAndData(header: IntArray, data: IntArray): IntArray =
        intArrayOf(header.size, data.size, *header, *data)

    private fun String.toAsciiInts(): IntArray = this.map { it.code }.toIntArray()

    private fun intsToByteArray(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
}
