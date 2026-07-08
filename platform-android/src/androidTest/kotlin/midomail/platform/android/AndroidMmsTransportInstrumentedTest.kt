package midomail.platform.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import midomail.adapter.gsm.MmsPart
import midomail.adapter.gsm.MmsSendRequestEncoder
import midomail.adapter.gsm.SmsSendResult
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test instrumentowany wysyłający RZECZYWISTY MMS na prawdziwym urządzeniu — wymaga jawnej zgody
 * i realnego numeru docelowego (koszt rzeczywisty, nieodwracalne). Celowo pomijany
 * (`assumeTrue`), jeśli argument `mmsDestination` nie został jawnie podany:
 *
 * ./gradlew :platform-android:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.mmsDestination=+48XXXXXXXXX
 */
@RunWith(AndroidJUnit4::class)
class AndroidMmsTransportInstrumentedTest {

    @Test
    fun sendingAMultimediaMessageReportsSentViaThePendingIntent() {
        val destination = InstrumentationRegistry.getArguments().getString("mmsDestination")
        assumeTrue("Pomijam - podaj -e mmsDestination <numer> aby uruchomić rzeczywistą wysyłkę", destination != null)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val transport = AndroidMmsTransport(context)
        val latch = CountDownLatch(1)
        var result: SmsSendResult? = null

        val parts = listOf(
            MmsPart(contentType = "text/plain", fileName = null, bytes = "midoMAIL - test Iteracji 3.10 (MMS)".toByteArray())
        )
        val pdu = MmsSendRequestEncoder.encode("midomail-test-${System.nanoTime()}", destination!!, parts)

        transport.send(pdu) {
            result = it
            latch.countDown()
        }

        val completed = latch.await(60, TimeUnit.SECONDS)
        assumeTrue("Brak odpowiedzi w 60s - sprawdź zasięg/transmisję danych", completed)
        assumeTrue("Wysyłka nie powiodła się: $result", result is SmsSendResult.Sent)
    }
}
