package midomail.domain.notification

import midomail.domain.event.SourceComponent
import midomail.domain.health.Alert
import midomail.domain.health.AlertId
import midomail.domain.health.AlertLevel
import midomail.domain.health.AlertStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs

class PushNotificationChannelTest {

    @Test
    fun `deliver always returns Unavailable`() {
        val channel = PushNotificationChannel()
        val alert = Alert(
            alertId = AlertId("alert-1"),
            level = AlertLevel.CRITICAL,
            source = SourceComponent("gsm-primary"),
            timestamp = Instant.now(),
            status = AlertStatus.ACTIVE
        )

        val result = channel.deliver(alert)

        assertIs<NotificationResult.Unavailable>(result)
    }
}
