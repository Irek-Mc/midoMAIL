package midomail.adapter.email

import midomail.domain.adapter.AdapterConfiguration

/**
 * Konfiguracja adaptera Email (20-Adapters/22-Adapter-Email.md, §8;
 * SPEC-0005-Configuration-Model.md, sekcja Adapters).
 */
data class EmailAdapterConfiguration(
    val smtp: SmtpConfig,
    val imap: ImapConfig,
    val pollIntervalMillis: Long
) : AdapterConfiguration
