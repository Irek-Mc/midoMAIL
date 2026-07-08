package midomail.platform.android

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Obsługa „Quick Response" (`RESPOND_VIA_MESSAGE_ACTION`) — wymagany komponent manifestu dla roli
 * domyślnej aplikacji SMS/MMS (40-Platforms/40-Android.md, §6). Pusta implementacja spełniająca
 * wymóg systemowy dla przyznania roli — midoMAIL nie prezentuje tradycyjnego UI konwersacji
 * (ADR-0003-Domyslna-aplikacja-SMS-MMS.md).
 */
class HeadlessSmsSendService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
