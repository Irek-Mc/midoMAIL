package midomail.adapter.gsm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager

/**
 * Port wysyłki SMS — pozwala [GsmAdapter] pozostać testowalnym atrapą, niezależnie od
 * rzeczywistego, zależnego od Androida punktu styku ([SmsSender]).
 */
interface SmsTransport {
    fun send(
        destinationAddress: String,
        content: String,
        onSentResult: (SmsSendResult) -> Unit,
        onDeliveredResult: (SmsSendResult) -> Unit = {}
    )
}

/**
 * Wysyłka SMS (20-Adapters/21-Adapter-GSM.md, §6). Zawsze `sendMultipartTextMessage` — nigdy
 * `sendTextMessage` dla treści przekraczającej pojedynczy segment (40-Platforms/40-Android.md, §5)
 * — segmentacja przez `SmsManager.divideMessage`, niezależna od decyzji o obcięciu
 * ([SmsContentTruncator], Iteracja 3.6, dokonanej wcześniej przez wywołującego).
 *
 * [onSentResult]/[onDeliveredResult] są wywoływane asynchronicznie, osobno dla każdego segmentu,
 * na podstawie wyniku dostarczonego przez `PendingIntent SENT`/`DELIVERED` — jedyne źródło prawdy
 * o dostarczeniu, zgodnie z 40-Platforms/40-Android.md, §5 ("brak wyjątku nie oznacza sukcesu").
 * Potwierdzenie DELIVERED zależy od wsparcia operatora/sieci — nie każda wiadomość je otrzyma,
 * nawet gdy SENT zakończyło się sukcesem. Ten cienki, zależny od Androida punkt styku jest celowo
 * bez testów jednostkowych (50-Quality/50-Testy.md, §5) — weryfikowany wyłącznie na rzeczywistym
 * urządzeniu z aktywną kartą SIM.
 */
class SmsSender(private val context: Context) : SmsTransport {

    override fun send(
        destinationAddress: String,
        content: String,
        onSentResult: (SmsSendResult) -> Unit,
        onDeliveredResult: (SmsSendResult) -> Unit
    ) {
        val smsManager = resolveSmsManager()
        val parts = smsManager.divideMessage(content)
        val sentIntents = ArrayList(
            parts.indices.map { index -> createResultPendingIntent(ACTION_SMS_SENT, index, onSentResult) }
        )
        val deliveryIntents = ArrayList(
            parts.indices.map { index -> createResultPendingIntent(ACTION_SMS_DELIVERED, index, onDeliveredResult) }
        )

        smsManager.sendMultipartTextMessage(destinationAddress, null, parts, sentIntents, deliveryIntents)
    }

    /**
     * `context.getSystemService(SmsManager::class.java)` zwraca `null` na części urządzeń/wersji
     * API — potwierdzone empirycznie na urządzeniu testowym (Redmi Note 4, Android 9/API 28).
     * `SmsManager.getDefault()` (przestarzały od API 31, ale nadal działający) jest niezawodnym
     * fallbackiem obejmującym wszystkie wspierane API. Multi-SIM (`getSmsManagerForSubscriptionId`)
     * jest opcjonalną możliwością poza zakresem tej iteracji (21-Adapter-GSM.md, §8).
     */
    @Suppress("DEPRECATION")
    private fun resolveSmsManager(): SmsManager =
        context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()

    private fun createResultPendingIntent(actionPrefix: String, index: Int, onResult: (SmsSendResult) -> Unit): PendingIntent {
        val action = "$actionPrefix.$index.${System.nanoTime()}"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                onResult(SmsSendResultInterpreter.interpret(resultCode))
                receiverContext.unregisterReceiver(this)
            }
        }
        registerReceiver(receiver, IntentFilter(action))

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, index, Intent(action), flags)
    }

    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private companion object {
        const val ACTION_SMS_SENT = "midomail.adapter.gsm.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "midomail.adapter.gsm.SMS_DELIVERED"
    }
}
