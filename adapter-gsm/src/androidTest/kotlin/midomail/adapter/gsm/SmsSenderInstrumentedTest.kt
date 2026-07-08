package midomail.adapter.gsm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test instrumentowany wysyłający RZECZYWISTY SMS na prawdziwym urządzeniu — wymaga jawnej zgody
 * i realnego numeru docelowego (koszt rzeczywisty, nieodwracalne). Celowo pomijany
 * (`assumeTrue`), jeśli argument `smsDestination` nie został jawnie podany:
 *
 * ./gradlew :adapter-gsm:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.smsDestination=+48XXXXXXXXX
 */
@RunWith(AndroidJUnit4::class)
class SmsSenderInstrumentedTest {

    @Test
    fun sendingATextMessageReportsSentViaThePendingIntent() {
        val destination = InstrumentationRegistry.getArguments().getString("smsDestination")
        assumeTrue("Pomijam - podaj -e smsDestination <numer> aby uruchomić rzeczywistą wysyłkę", destination != null)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sender = SmsSender(context)
        val sentLatch = CountDownLatch(1)
        var sentResult: SmsSendResult? = null

        sender.send(
            destinationAddress = destination!!,
            content = "midoMAIL - test Iteracji 3.7 (SmsSenderInstrumentedTest)",
            onSentResult = {
                sentResult = it
                sentLatch.countDown()
            }
        )

        val completed = sentLatch.await(30, TimeUnit.SECONDS)
        assumeTrue("Brak odpowiedzi od radia w 30s - sprawdź zasięg", completed)
        assumeTrue("Wysyłka nie powiodła się: $sentResult", sentResult is SmsSendResult.Sent)
    }
}
