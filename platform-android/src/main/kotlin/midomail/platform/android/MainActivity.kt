package midomail.platform.android

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * Ekran statusu i konfiguracji (ADR-0038-Ekran-Statusu-i-Konfiguracji-Android.md) — zastępuje
 * pusty ekran startowy z Iteracji 3.0. midoMAIL nadal nie prezentuje tradycyjnego UI konwersacji
 * (ADR-0003-Domyslna-aplikacja-SMS-MMS.md) — ten ekran to status/konfiguracja gatewaya, nie
 * interfejs SMS.
 *
 * Iteracja 3.2: uruchamia [GatewayForegroundService] — od Iteracji 3.12 właściwe miejsce
 * kompozycji Registry/GatewayEngine/GsmAdapter.
 *
 * Iteracja 3.3: żąda uprawnień runtime SMS/MMS i roli domyślnej aplikacji SMS/MMS — warunek
 * wstępny Fazy 3, nie odroczony (ADR-0003). `RoleManager.ROLE_SMS` na Android 10+ (API 29+),
 * `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT` na starszych (40-Platforms/40-Android.md, §6) —
 * urządzenie testowe (Redmi Note 4, LineageOS, Android 9/API 28) ćwiczy wyłącznie drugą ścieżkę.
 */
class MainActivity : Activity() {

    private lateinit var secretStore: AndroidKeystoreSecretStore
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            refreshHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        secretStore = AndroidKeystoreSecretStore(applicationContext)

        startForegroundService(Intent(this, GatewayForegroundService::class.java))
        requestMissingSmsPermissions()

        prefillFormFromSecrets()
        findViewById<Button>(R.id.saveAndRestartButton).setOnClickListener { onSaveAndRestartClicked() }
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_SMS_PERMISSIONS) {
            requestDefaultSmsAppRoleIfNeeded()
        }
    }

    private fun requestMissingSmsPermissions() {
        val missing = REQUIRED_SMS_PERMISSIONS.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            requestDefaultSmsAppRoleIfNeeded()
        } else {
            requestPermissions(missing.toTypedArray(), REQUEST_CODE_SMS_PERMISSIONS)
        }
    }

    private fun requestDefaultSmsAppRoleIfNeeded() {
        if (isDefaultSmsApp()) {
            return
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivityForResult(intent, REQUEST_CODE_DEFAULT_SMS_APP)
    }

    private fun isDefaultSmsApp(): Boolean = Telephony.Sms.getDefaultSmsPackage(this) == packageName

    // --- Ekran statusu (ADR-0038) ---

    private fun refreshStatus() {
        val running = GatewayForegroundService.instance != null
        findViewById<TextView>(R.id.serviceStatusText).text =
            getString(if (running) R.string.status_service_running else R.string.status_service_stopped)

        findViewById<TextView>(R.id.defaultSmsAppStatusText).text =
            getString(if (isDefaultSmsApp()) R.string.status_default_sms_yes else R.string.status_default_sms_no)

        val adapters = GatewayForegroundService.instance?.statusSnapshot().orEmpty()
        findViewById<TextView>(R.id.adaptersStatusText).text = if (adapters.isEmpty()) {
            getString(R.string.no_adapters_registered)
        } else {
            adapters.joinToString("\n") { adapter ->
                getString(
                    R.string.adapter_status_format,
                    adapter.adapterId,
                    getString(if (adapter.healthy) R.string.adapter_healthy else R.string.adapter_unhealthy),
                    adapter.messagesSent,
                    adapter.messagesReceived
                )
            }
        }
    }

    // --- Formularz konfiguracji (ADR-0038) ---

    private fun prefillFormFromSecrets() {
        val smtp = resolveHostPort(secretStore, GatewayForegroundService.SECRET_REF_SMTP_HOST, GatewayForegroundService.SECRET_REF_SMTP_PORT, "smtp.gmail.com", 587)
        val imap = resolveHostPort(secretStore, GatewayForegroundService.SECRET_REF_IMAP_HOST, GatewayForegroundService.SECRET_REF_IMAP_PORT, "imap.gmail.com", 993)

        findViewById<EditText>(R.id.smtpHostInput).setText(smtp.host)
        findViewById<EditText>(R.id.smtpPortInput).setText(String.format(java.util.Locale.ROOT, "%d", smtp.port))
        findViewById<EditText>(R.id.imapHostInput).setText(imap.host)
        findViewById<EditText>(R.id.imapPortInput).setText(String.format(java.util.Locale.ROOT, "%d", imap.port))
        findViewById<EditText>(R.id.usernameInput).setText(secretStore.read(GatewayForegroundService.SECRET_REF_USERNAME).orEmpty())
        findViewById<EditText>(R.id.passwordInput).setText(secretStore.read(GatewayForegroundService.SECRET_REF_PASSWORD).orEmpty())
        findViewById<EditText>(R.id.forwardToInput).setText(secretStore.read(GatewayForegroundService.SECRET_REF_FORWARD_TO).orEmpty())
        findViewById<EditText>(R.id.webhookUrlInput).setText(secretStore.read(GatewayForegroundService.SECRET_REF_WEBHOOK_URL).orEmpty())
    }

    private fun onSaveAndRestartClicked() {
        val username = findViewById<EditText>(R.id.usernameInput).text.toString().trim()
        val password = findViewById<EditText>(R.id.passwordInput).text.toString()
        val forwardTo = findViewById<EditText>(R.id.forwardToInput).text.toString().trim()

        val validationError = findViewById<TextView>(R.id.validationErrorText)
        if (username.isEmpty() || password.isEmpty() || forwardTo.isEmpty()) {
            validationError.text = getString(R.string.validation_error_required_fields)
            validationError.visibility = View.VISIBLE
            return
        }
        validationError.visibility = View.GONE

        secretStore.write(GatewayForegroundService.SECRET_REF_USERNAME, username)
        secretStore.write(GatewayForegroundService.SECRET_REF_PASSWORD, password)
        secretStore.write(GatewayForegroundService.SECRET_REF_FORWARD_TO, forwardTo)
        secretStore.write(GatewayForegroundService.SECRET_REF_SMTP_HOST, findViewById<EditText>(R.id.smtpHostInput).text.toString().trim())
        secretStore.write(GatewayForegroundService.SECRET_REF_SMTP_PORT, findViewById<EditText>(R.id.smtpPortInput).text.toString().trim())
        secretStore.write(GatewayForegroundService.SECRET_REF_IMAP_HOST, findViewById<EditText>(R.id.imapHostInput).text.toString().trim())
        secretStore.write(GatewayForegroundService.SECRET_REF_IMAP_PORT, findViewById<EditText>(R.id.imapPortInput).text.toString().trim())
        val webhookUrl = findViewById<EditText>(R.id.webhookUrlInput).text.toString().trim()
        if (webhookUrl.isNotEmpty()) {
            secretStore.write(GatewayForegroundService.SECRET_REF_WEBHOOK_URL, webhookUrl)
        }

        android.widget.Toast.makeText(this, R.string.save_success, android.widget.Toast.LENGTH_SHORT).show()
        restartGatewayService()
    }

    private fun restartGatewayService() {
        stopService(Intent(this, GatewayForegroundService::class.java))
        startForegroundService(Intent(this, GatewayForegroundService::class.java))
    }

    private companion object {
        const val REQUEST_CODE_SMS_PERMISSIONS = 1
        const val REQUEST_CODE_DEFAULT_SMS_APP = 2
        const val STATUS_REFRESH_INTERVAL_MILLIS = 3000L
        val REQUIRED_SMS_PERMISSIONS = listOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_MMS,
            android.Manifest.permission.RECEIVE_WAP_PUSH
        )
    }
}
