package midomail.platform.android

/**
 * Odbiornik WYŁĄCZNIE do ręcznej weryfikacji produkcyjnej (Iteracja 3.13, analogicznie do
 * harnessu ręcznej weryfikacji Fazy 2, adapter-email/src/manualVerification/) — jedyny sposób
 * wprowadzenia hasła aplikacji Gmail na urządzenie testowe bez trwałego UI (które nie istnieje w
 * tej fazie). Dane trafiają WYŁĄCZNIE do [midomail.platform.android.AndroidKeystoreSecretStore],
 * nigdy do kodu/repozytorium/logów — wywoływany wyłącznie ręcznie przez `adb shell am broadcast`,
 * nie jest częścią żadnego przepływu użytkownika.
 *
 * `android:exported="false"` w manifeście — dostępny wyłącznie dla `adb shell` (uprzywilejowany
 * UID powłoki), nie dla innych aplikacji na urządzeniu.
 */
class DebugCredentialProvisioningReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        if (intent.action != ACTION_PROVISION_CREDENTIAL) return
        val reference = intent.getStringExtra(EXTRA_REFERENCE) ?: return
        val value = intent.getStringExtra(EXTRA_VALUE) ?: return

        AndroidKeystoreSecretStore(context.applicationContext).write(reference, value)
    }

    companion object {
        const val ACTION_PROVISION_CREDENTIAL = "midomail.platform.android.PROVISION_CREDENTIAL"
        const val EXTRA_REFERENCE = "reference"
        const val EXTRA_VALUE = "value"
    }
}
