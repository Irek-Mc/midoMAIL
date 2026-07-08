package midomail.platform.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import midomail.adapter.gsm.MmsTransport
import midomail.adapter.gsm.SmsSendResult
import midomail.adapter.gsm.SmsSendResultInterpreter
import java.io.File

/**
 * Implementacja [MmsTransport] przez `SmsManager.sendMultimediaMessage` — zapisuje zakodowane PDU
 * (Iteracja 3.10, [midomail.adapter.gsm.MmsSendRequestEncoder]) pod `content://` Uri
 * ([androidx.core.content.FileProvider], ta sama konfiguracja co odbiór — [MmsWapPushReceiver])
 * i woła rzeczywistą wysyłkę. Wynik interpretowany tym samym mechanizmem co SMS
 * ([SmsSendResultInterpreter]) — `PendingIntent` jest jedynym źródłem prawdy o wyniku.
 */
class AndroidMmsTransport(private val context: Context) : MmsTransport {

    override fun send(pdu: ByteArray, onResult: (SmsSendResult) -> Unit) {
        val appContext = context.applicationContext
        val file = File(File(appContext.cacheDir, "mms").apply { mkdirs() }, "send-${System.nanoTime()}.dat")
        file.writeBytes(pdu)
        val contentUri = FileProvider.getUriForFile(appContext, FILE_PROVIDER_AUTHORITY, file)

        val sentIntent = createSentPendingIntent(appContext, file, onResult)
        resolveSmsManager(appContext).sendMultimediaMessage(appContext, contentUri, null, null, sentIntent)
    }

    private fun createSentPendingIntent(context: Context, file: File, onResult: (SmsSendResult) -> Unit): PendingIntent {
        val action = "midomail.adapter.gsm.MMS_SENT.${System.nanoTime()}"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                receiverContext.unregisterReceiver(this)
                file.delete()
                onResult(SmsSendResultInterpreter.interpret(resultCode))
            }
        }
        registerReceiver(context, receiver, IntentFilter(action))

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, Intent(action), flags)
    }

    @Suppress("DEPRECATION")
    private fun resolveSmsManager(context: Context): SmsManager =
        context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()

    private fun registerReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private companion object {
        const val FILE_PROVIDER_AUTHORITY = "midomail.platform.android.mmsfileprovider"
    }
}
