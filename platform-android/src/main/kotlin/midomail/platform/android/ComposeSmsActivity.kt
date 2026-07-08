package midomail.platform.android

import android.app.Activity
import android.os.Bundle

/**
 * Obsługa `ACTION_SENDTO` (sms/smsto/mms/mmsto) — wymagany komponent manifestu dla roli domyślnej
 * aplikacji SMS/MMS (40-Platforms/40-Android.md, §6). midoMAIL nie prezentuje tradycyjnego UI
 * konwersacji (ADR-0003-Domyslna-aplikacja-SMS-MMS.md) — ta aktywność istnieje wyłącznie jako
 * spełnienie wymogu systemowego dla przyznania roli, kończy się natychmiast.
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
