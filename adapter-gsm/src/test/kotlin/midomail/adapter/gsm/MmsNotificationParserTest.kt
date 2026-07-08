package midomail.adapter.gsm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MmsNotificationParserTest {

    @Test
    fun `parses a minimal well-formed M-Notification-ind PDU`() {
        val pdu = buildList {
            add(0x8C); add(0x82) // Message-Type = m-notification-ind
            add(0x98); addAll("TX-42".toAsciiBytesWithNullTerminator()) // Transaction-Id
            add(0x8D); add(0x90) // MMS-Version = 1.0
            add(0x83); addAll("http://mmsc.example.com/msg/1".toAsciiBytesWithNullTerminator()) // Content-Location
        }.toByteArrayFromInts()

        val notification = MmsNotificationParser.parse(pdu)

        assertEquals(MmsNotification("TX-42", "http://mmsc.example.com/msg/1"), notification)
    }

    @Test
    fun `skips unrelated header fields before finding Content-Location`() {
        val pdu = buildList {
            add(0x8C); add(0x82)
            add(0x98); addAll("TX-99".toAsciiBytesWithNullTerminator())
            add(0x8D); add(0x90)
            // Symulacja innych, niededukowanych pól - kilka nieszkodliwych bajtów przed Content-Location.
            add(0x8A); add(0x80)
            add(0x83); addAll("https://mmsc.example.com/msg/2".toAsciiBytesWithNullTerminator())
        }.toByteArrayFromInts()

        val notification = MmsNotificationParser.parse(pdu)

        assertEquals("https://mmsc.example.com/msg/2", notification?.contentLocationUrl)
    }

    @Test
    fun `a leading quote byte before a text string is skipped correctly`() {
        val pdu = buildList {
            add(0x8C); add(0x82)
            add(0x98); add(0x7F); addAll("TX-quoted".toAsciiBytesWithNullTerminator())
            add(0x8D); add(0x90)
            add(0x83); addAll("http://mmsc.example.com/msg/3".toAsciiBytesWithNullTerminator())
        }.toByteArrayFromInts()

        val notification = MmsNotificationParser.parse(pdu)

        assertEquals("TX-quoted", notification?.transactionId)
    }

    @Test
    fun `a PDU that is not m-notification-ind is rejected`() {
        val pdu = buildList {
            add(0x8C); add(0x80) // m-send-req, not m-notification-ind
            add(0x98); addAll("TX-1".toAsciiBytesWithNullTerminator())
            add(0x8D); add(0x90)
        }.toByteArrayFromInts()

        assertNull(MmsNotificationParser.parse(pdu))
    }

    @Test
    fun `a PDU missing Content-Location returns null`() {
        val pdu = buildList {
            add(0x8C); add(0x82)
            add(0x98); addAll("TX-1".toAsciiBytesWithNullTerminator())
            add(0x8D); add(0x90)
        }.toByteArrayFromInts()

        assertNull(MmsNotificationParser.parse(pdu))
    }

    @Test
    fun `truncated PDU returns null instead of throwing`() {
        val pdu = byteArrayOf(0x8C.toByte())

        assertNull(MmsNotificationParser.parse(pdu))
    }

    private fun String.toAsciiBytesWithNullTerminator(): List<Int> =
        this.map { it.code } + listOf(0x00)

    private fun List<Int>.toByteArrayFromInts(): ByteArray =
        this.map { it.toByte() }.toByteArray()
}
