package midomail.adapter.gsm

/**
 * Wynik wysyłki jednego segmentu SMS (20-Adapters/21-Adapter-GSM.md, §6;
 * 40-Platforms/40-Android.md, §5) — jedynym źródłem prawdy jest wynik dostarczony przez
 * `PendingIntent SENT`, nigdy brak wyjątku z samego wywołania `sendMultipartTextMessage`
 * (wywołanie jest asynchroniczne — brak wyjątku oznacza tylko, że żądanie zostało przyjęte przez
 * radio, nie że dotarło).
 */
sealed interface SmsSendResult {
    data object Sent : SmsSendResult
    data class Failed(val reason: String) : SmsSendResult
}

/**
 * Interpretacja surowego kodu wyniku z `PendingIntent SENT` (stałe `Activity.RESULT_OK` /
 * `SmsManager.RESULT_ERROR_*`) — czysta funkcja, testowalna bez urządzenia (stałe całkowite są
 * wkompilowywane przez kompilator, nie wymagają rzeczywistej implementacji frameworka Android).
 */
object SmsSendResultInterpreter {
    fun interpret(resultCode: Int): SmsSendResult = when (resultCode) {
        android.app.Activity.RESULT_OK -> SmsSendResult.Sent
        android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE -> SmsSendResult.Failed("GENERIC_FAILURE")
        android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> SmsSendResult.Failed("NO_SERVICE")
        android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> SmsSendResult.Failed("NULL_PDU")
        android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> SmsSendResult.Failed("RADIO_OFF")
        else -> SmsSendResult.Failed("UNKNOWN_$resultCode")
    }
}
