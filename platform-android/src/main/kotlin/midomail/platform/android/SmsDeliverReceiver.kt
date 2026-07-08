package midomail.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import midomail.adapter.gsm.GsmRuntime
import midomail.adapter.gsm.SmsPduConcatenator
import midomail.adapter.gsm.SmsSegment

/**
 * Odbiornik `SMS_DELIVER_ACTION` — wymagany komponent manifestu dla roli domyślnej aplikacji
 * SMS/MMS (40-Platforms/40-Android.md, §6; ADR-0003-Domyslna-aplikacja-SMS-MMS.md). Celowo
 * `SMS_DELIVER_ACTION`, nie `SMS_RECEIVED_ACTION` (dostępny tylko dla aplikacji niedomyślnych).
 *
 * Sprawdzenie `intent.action` mimo `android:permission` w manifeście — obrona w głąb przed
 * podszytą intencją (Android Lint: `UnsafeProtectedBroadcastReceiver`).
 *
 * Ekstrakcja z `android.telephony.SmsMessage`/PDU jest celowo bez testów jednostkowych
 * (50-Quality/50-Testy.md, §5) — weryfikowana wyłącznie na rzeczywistym urządzeniu. Scalanie
 * wieloczęściowych wiadomości ([SmsPduConcatenator]) i mapowanie ([SmsMessageMapper]) są czystym
 * Kotlinem, testowane osobno.
 *
 * `GsmRuntime` (nie konstruktor) — ten odbiornik jest instancjonowany bezargumentowo przez system
 * Android na podstawie deklaracji w manifeście, nie przez `AdapterFactory`.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val segments = messages.map { message ->
            SmsSegment(
                sender = message.originatingAddress ?: return,
                body = message.messageBody ?: "",
                timestampMillis = message.timestampMillis
            )
        }
        val combined = SmsPduConcatenator.concatenate(segments)

        val mapper = GsmRuntime.mapper ?: return
        val gatewayInbound = GsmRuntime.gatewayInbound ?: return
        gatewayInbound.receive(mapper.fromSms(combined.sender, combined.body, combined.timestampMillis))
    }
}
