package midomail.adapter.email.manualverification

/**
 * Dane logowania wyłącznie ze zmiennych środowiskowych (docs/faza2-weryfikacja-gmail.md, §6) —
 * nigdy w kodzie, repozytorium ani w logu. `toString()` celowo nie ujawnia [appPassword].
 */
data class GmailTestConfig(
    val address: String,
    val appPassword: String,
    val correspondentAddress: String?
) {
    override fun toString(): String =
        "GmailTestConfig(address=$address, appPassword=***, correspondentAddress=$correspondentAddress)"

    companion object {
        fun fromEnvironment(): GmailTestConfig {
            val address = System.getenv("GMAIL_TEST_ADDRESS")
                ?: error("Brak zmiennej środowiskowej GMAIL_TEST_ADDRESS (docs/faza2-weryfikacja-gmail.md, §6)")
            val appPassword = System.getenv("GMAIL_TEST_APP_PASSWORD")
                ?: error("Brak zmiennej środowiskowej GMAIL_TEST_APP_PASSWORD (docs/faza2-weryfikacja-gmail.md, §6)")
            val correspondent = System.getenv("GMAIL_CORRESPONDENT_ADDRESS")
            return GmailTestConfig(address, appPassword, correspondent)
        }
    }
}
