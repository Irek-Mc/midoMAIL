package midomail.domain.administration

/**
 * Schemat pól konfiguracji per typ adaptera (ADR-0031-Adapter-Typed-Configuration.md,
 * 64-Adaptery.md §6) — nazwy pól jako `String`, NIE typy Kotlin z `:adapter-email`/`:adapter-gsm`
 * (te moduły są siostrzane względem `:adapter-rest`/`:adapter-cli`, żaden nie może zależeć od
 * drugiego). Ręcznie utrzymywana kopia kształtu `EmailAdapterConfiguration`/`GsmAdapterConfiguration`
 * — ryzyko rozjazdu świadomie zaakceptowane (patrz ADR-0031, §Konsekwencje).
 */
object AdapterConfigurationSchema {

    private val EMAIL_FIELDS = listOf(
        "smtp.host", "smtp.port", "smtp.ssl", "smtp.starttls", "smtp.username", "smtp.password",
        "imap.host", "imap.port", "imap.imaps", "imap.starttls", "imap.username", "imap.password",
        "imap.folder", "imap.pollIntervalMillis"
    )

    private val GSM_FIELDS = listOf("maxSmsSegments", "forwardToAddress")

    /** Pola sekretne — nigdy nie zwracane przez `AdapterConfigurationAdministration.read()`. */
    val SECRET_FIELDS: Set<String> = setOf("smtp.password", "imap.password")

    fun fieldsFor(adapterType: String): List<String> = when (adapterType) {
        "email" -> EMAIL_FIELDS
        "gsm" -> GSM_FIELDS
        else -> emptyList()
    }
}
