package midomail.adapter.gsm

import midomail.domain.adapter.AdapterConfiguration

/**
 * Konfiguracja Adaptera GSM (20-Adapters/21-Adapter-GSM.md, §6; SPEC-0005-Configuration-Model.md,
 * sekcja Adapters). Brak pól poświadczeń — GSM nie wymaga uwierzytelniania (lokalny modem/SIM, w
 * przeciwieństwie do SMTP/IMAP w Adapterze Email — potwierdzone jawnie w Iteracji 3.4).
 *
 * [maxSmsSegments] — maksymalna liczba segmentów Multipart SMS przed obcięciem treści
 * (ADR-0009-Obciecie-Tresci-SMS.md); `simSlot` (multi-SIM, §8) świadomie poza zakresem Fazy 3
 * (opcjonalna możliwość, nieużywana przez tę implementację).
 *
 * [forwardToAddress] — adres, na który przekazywane są przychodzące SMS/MMS po trasowaniu do
 * innego kanału (Iteracja 3.13, `SmsMessageMapper`/`MmsMessageMapper`); `null` pozostawia adres
 * docelowy nieustawiony (poprzednie zachowanie, Iteracja 3.5/3.9).
 */
data class GsmAdapterConfiguration(
    val maxSmsSegments: Int = 3,
    val forwardToAddress: String? = null
) : AdapterConfiguration
