package midomail.domain.message

import midomail.domain.adapter.AdapterId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Potwierdza kontrakt Channel z ADR-0010-Model-Channel.md, ADR-0012-Channel-AdapterId.md i
 * SPEC-0001-GatewayMessage.md, §Channel: ChannelType obowiązkowy i niepusty, address i adapterId
 * opcjonalne i nieinterpretowane przez Core.
 */
class ChannelTest {

    @Test
    fun `ChannelType rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { ChannelType("") }
        assertFailsWith<IllegalArgumentException> { ChannelType("   ") }
    }

    @Test
    fun `address defaults to null when not specified`() {
        val channel = Channel(type = ChannelType("gsm"))

        assertNull(channel.address)
    }

    @Test
    fun `adapterId defaults to null when not specified`() {
        val channel = Channel(type = ChannelType("gsm"))

        assertNull(channel.adapterId)
    }

    @Test
    fun `adapterId can be set immediately for a source channel`() {
        val channel = Channel(
            type = ChannelType("gsm"),
            address = "+48500111222",
            adapterId = AdapterId("gsm-primary")
        )

        assertEquals(AdapterId("gsm-primary"), channel.adapterId)
    }

    @Test
    fun `AdapterId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> { AdapterId("") }
    }

    @Test
    fun `address accepts a phone-number-shaped value without interpretation`() {
        val channel = Channel(type = ChannelType("gsm"), address = "+48500111222")

        assertEquals("+48500111222", channel.address)
    }

    @Test
    fun `address accepts an email-shaped value without interpretation`() {
        val channel = Channel(type = ChannelType("email"), address = "user@example.com")

        assertEquals("user@example.com", channel.address)
    }

    @Test
    fun `address accepts an arbitrary opaque value - Core does not validate its format`() {
        // Core nie zna ani nie waliduje semantyki address (ADR-0010) — dowolny ciąg jest
        // dopuszczalny, format jest sprawą wyłącznie adaptera po drugiej stronie.
        val channel = Channel(type = ChannelType("webhook"), address = "https://example.com/hook")

        assertEquals("https://example.com/hook", channel.address)
    }
}
