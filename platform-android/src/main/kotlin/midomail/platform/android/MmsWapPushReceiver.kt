package midomail.platform.android

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import midomail.adapter.gsm.GsmRuntime
import midomail.adapter.gsm.MmsMessage
import midomail.adapter.gsm.MmsNotificationParser
import midomail.adapter.gsm.MmsRetrieveConfParser
import java.io.File

/**
 * Odbiornik `WAP_PUSH_DELIVER_ACTION` (odbiór MMS) — wymagany komponent manifestu dla roli
 * domyślnej aplikacji SMS/MMS (40-Platforms/40-Android.md, §6; ADR-0003-Domyslna-aplikacja-SMS-MMS.md).
 * Bez tej roli platforma nie gwarantuje dostarczenia zdarzenia o przychodzącym MMS.
 *
 * Sprawdzenie `intent.action` mimo `android:permission` w manifeście — obrona w głąb przed
 * podszytą intencją (Android Lint: `UnsafeProtectedBroadcastReceiver`).
 *
 * Przepływ (20-Adapters/21-Adapter-GSM.md, §7): powiadomienie push (bajty PDU pod kluczem `data`)
 * → [MmsNotificationParser] wyodrębnia adres pobrania → `SmsManager.downloadMultimediaMessage`
 * pobiera rzeczywistą treść pod współdzielony `content://` Uri ([androidx.core.content.FileProvider])
 * → po zakończeniu pobierania [MmsRetrieveConfParser] dekoduje pobrane PDU → [GsmRuntime.mmsMapper]
 * mapuje na GatewayMessage → [GsmRuntime.gatewayInbound].
 *
 * Parsowanie PDU jest celowo bez testów jednostkowych na tym poziomie (50-Quality/50-Testy.md, §5)
 * — logika samych parserów jest testowana osobno ([MmsNotificationParser]/[MmsRetrieveConfParser]
 * testy jednostkowe), ten cienki punkt styku z `SmsManager`/`FileProvider` weryfikowany wyłącznie
 * na rzeczywistym urządzeniu.
 */
class MmsWapPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            return
        }

        val pdu = intent.getByteArrayExtra("data") ?: return
        val notification = MmsNotificationParser.parse(pdu) ?: return

        val appContext = context.applicationContext
        val downloadFile = downloadFileFor(appContext, notification.transactionId)
        val contentUri = FileProvider.getUriForFile(appContext, FILE_PROVIDER_AUTHORITY, downloadFile)
        val downloadedIntent = createDownloadCompletePendingIntent(appContext, downloadFile)

        resolveSmsManager(appContext).downloadMultimediaMessage(
            appContext,
            notification.contentLocationUrl,
            contentUri,
            null,
            downloadedIntent
        )
    }

    private fun downloadFileFor(context: Context, transactionId: String): File {
        val directory = File(context.cacheDir, "mms").apply { mkdirs() }
        return File(directory, "$transactionId-${System.nanoTime()}.dat")
    }

    private fun createDownloadCompletePendingIntent(context: Context, downloadFile: File): PendingIntent {
        val action = "midomail.adapter.gsm.MMS_DOWNLOADED.${System.nanoTime()}"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                receiverContext.unregisterReceiver(this)
                if (resultCode == Activity.RESULT_OK) {
                    handleDownloadedMms(downloadFile)
                }
                downloadFile.delete()
            }
        }
        registerReceiver(context, receiver, IntentFilter(action))

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, Intent(action), flags)
    }

    private fun handleDownloadedMms(downloadFile: File) {
        val pdu = downloadFile.readBytes()
        val parts = MmsRetrieveConfParser.parse(pdu)
        if (parts.isEmpty()) return
        val sender = MmsRetrieveConfParser.parseSender(pdu) ?: return

        val mapper = GsmRuntime.mmsMapper ?: return
        val gatewayInbound = GsmRuntime.gatewayInbound ?: return

        val mms = MmsMessage(sender = sender, timestampMillis = System.currentTimeMillis(), parts = parts)
        gatewayInbound.receive(mapper.fromMms(mms))
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
