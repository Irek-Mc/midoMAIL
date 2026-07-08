package midomail.adapter.gsm

import midomail.domain.event.Event
import midomail.domain.event.EventCategory
import midomail.domain.event.EventId
import midomail.domain.event.EventType
import midomail.domain.event.EventVersion
import midomail.domain.event.SourceComponent
import midomail.domain.message.CorrelationId
import midomail.domain.port.EventPublisher
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

/** Zdarzenie domenowe publikowane przy obcięciu treści SMS (ADR-0009-Obciecie-Tresci-SMS.md). */
data class SmsContentTruncated(val originalLength: Int, val truncatedLength: Int)

/**
 * Obcięcie treści przekraczającej skonfigurowany maksymalny limit segmentów Multipart SMS
 * (ADR-0009-Obciecie-Tresci-SMS.md; 20-Adapters/21-Adapter-GSM.md, §6).
 *
 * Segmentacja SMS zależy od alfabetu: GSM 7-bit (160 znaków w pojedynczym segmencie, 153 w
 * segmencie wieloczęściowym — 7 septetów zajmuje nagłówek UDH łączący segmenty) dla treści
 * mieszczącej się w całości w alfabecie GSM 03.38, albo UCS-2 (odpowiednio 70/67 znaków) gdy
 * choć jeden znak wykracza poza ten alfabet (np. polskie znaki diakrytyczne). Znaki z tablicy
 * rozszerzonej GSM 7-bit liczą się jako 2 septety.
 *
 * Treść przekraczająca limit jest obcinana z jawnym wskaźnikiem obcięcia („…") na końcu, nie
 * odrzucana w całości — dostarczenie skróconej, ale użytecznej treści jest cenniejsze niż brak
 * dostarczenia. Zdarzenie obcięcia jest publikowane przez [EventPublisher] (`AdapterPorts`
 * amendment, Iteracja 3.6) — nigdy nie zachodzi cicho.
 */
class SmsContentTruncator(
    private val maxSegments: Int,
    private val eventPublisher: EventPublisher
) {

    fun truncateIfNeeded(content: String, correlationId: CorrelationId): String {
        val gsm7 = isGsm7Compatible(content)
        val singleCapacity = if (gsm7) SINGLE_SEGMENT_GSM7 else SINGLE_SEGMENT_UCS2
        val multiCapacity = if (gsm7) MULTI_SEGMENT_GSM7 else MULTI_SEGMENT_UCS2

        val weight = contentWeight(content, gsm7)
        if (segmentsFor(weight, singleCapacity, multiCapacity) <= maxSegments) {
            return content
        }

        val maxWeight = if (maxSegments == 1) singleCapacity else maxSegments * multiCapacity
        val truncated = truncateToWeight(content, maxWeight - TRUNCATION_INDICATOR.length, gsm7) + TRUNCATION_INDICATOR

        publishTruncationEvent(content.length, truncated.length, correlationId)
        return truncated
    }

    private fun segmentsFor(weight: Int, singleCapacity: Int, multiCapacity: Int): Int =
        if (weight <= singleCapacity) 1 else ceil(weight.toDouble() / multiCapacity).toInt()

    private fun contentWeight(content: String, gsm7: Boolean): Int =
        if (gsm7) content.count() + content.count { it in GSM_7BIT_EXTENDED } else content.length

    private fun truncateToWeight(content: String, maxWeight: Int, gsm7: Boolean): String {
        if (!gsm7) return content.take(maxWeight.coerceAtLeast(0))

        val builder = StringBuilder()
        var weight = 0
        for (char in content) {
            val charWeight = if (char in GSM_7BIT_EXTENDED) 2 else 1
            if (weight + charWeight > maxWeight) break
            builder.append(char)
            weight += charWeight
        }
        return builder.toString()
    }

    private fun isGsm7Compatible(content: String): Boolean =
        content.all { it in GSM_7BIT_BASIC || it in GSM_7BIT_EXTENDED }

    private fun publishTruncationEvent(originalLength: Int, truncatedLength: Int, correlationId: CorrelationId) {
        eventPublisher.publish(
            Event(
                eventId = EventId(UUID.randomUUID().toString()),
                eventType = EventType("sms.content_truncated"),
                eventVersion = EventVersion("1.0"),
                category = EventCategory.DOMAIN,
                timestamp = Instant.now(),
                correlationId = correlationId,
                sourceComponent = SourceComponent("GsmAdapter"),
                payload = SmsContentTruncated(originalLength, truncatedLength)
            )
        )
    }

    private companion object {
        const val SINGLE_SEGMENT_GSM7 = 160
        const val MULTI_SEGMENT_GSM7 = 153
        const val SINGLE_SEGMENT_UCS2 = 70
        const val MULTI_SEGMENT_UCS2 = 67
        const val TRUNCATION_INDICATOR = "…"

        val GSM_7BIT_BASIC: Set<Char> = ("@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ ÆæßÉ" +
            " !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿" +
            "abcdefghijklmnopqrstuvwxyzäöñüà").toSet()

        val GSM_7BIT_EXTENDED: Set<Char> = "^{}\\[~]|€".toSet()
    }
}
