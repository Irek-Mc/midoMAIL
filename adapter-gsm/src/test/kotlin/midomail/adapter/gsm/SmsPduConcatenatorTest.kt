package midomail.adapter.gsm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SmsPduConcatenatorTest {

    @Test
    fun `a single segment is returned unchanged`() {
        val segment = SmsSegment(sender = "+48123456789", body = "hello", timestampMillis = 1_000L)

        val result = SmsPduConcatenator.concatenate(listOf(segment))

        assertEquals(segment, result)
    }

    @Test
    fun `multiple segments are concatenated in order using the first segment's sender and timestamp`() {
        val segments = listOf(
            SmsSegment(sender = "+48123456789", body = "part one, ", timestampMillis = 1_000L),
            SmsSegment(sender = "+48123456789", body = "part two, ", timestampMillis = 1_001L),
            SmsSegment(sender = "+48123456789", body = "part three", timestampMillis = 1_002L)
        )

        val result = SmsPduConcatenator.concatenate(segments)

        assertEquals("+48123456789", result.sender)
        assertEquals("part one, part two, part three", result.body)
        assertEquals(1_000L, result.timestampMillis)
    }

    @Test
    fun `concatenating an empty list of segments fails`() {
        assertFailsWith<IllegalArgumentException> {
            SmsPduConcatenator.concatenate(emptyList())
        }
    }
}
