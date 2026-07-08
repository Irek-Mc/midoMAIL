package midomail.adapter.email.manualverification

/**
 * Punkt wejścia harnessu ręcznej weryfikacji Gmail (docs/faza2-weryfikacja-gmail.md).
 *
 * Uruchomienie: `gradle :adapter-email:runManualVerification --args="scenario=<1-20>"`.
 * Wymaga zmiennych środowiskowych GMAIL_TEST_ADDRESS, GMAIL_TEST_APP_PASSWORD, opcjonalnie
 * GMAIL_CORRESPONDENT_ADDRESS i GMAIL_SECOND_CORRESPONDENT_ADDRESS (docs/faza2-weryfikacja-gmail.md, §6).
 *
 * Nie jest kodem produkcyjnym — nie zmienia, nie rozszerza i nie wpływa na Public API `:domain`
 * ani `EmailAdapter`; wyłącznie composition root spinający już istniejące, niezmienione klasy.
 */
fun main(args: Array<String>) {
    val scenarioArg = args.firstOrNull { it.startsWith("scenario=") }
        ?: error("Brak argumentu scenario=<1-20>, np. --args=\"scenario=1\"")
    val scenarioNumber = scenarioArg.substringAfter("scenario=").toIntOrNull()
        ?: error("Nieprawidłowy numer scenariusza w '$scenarioArg'")

    val config = GmailTestConfig.fromEnvironment()
    val correspondent = config.correspondentAddress
        ?: error("Scenariusz $scenarioNumber wymaga GMAIL_CORRESPONDENT_ADDRESS")
    val secondCorrespondent = System.getenv("GMAIL_SECOND_CORRESPONDENT_ADDRESS")

    VerificationLog.scenarioStart(scenarioNumber, scenarioName(scenarioNumber))

    val result = when (scenarioNumber) {
        1 -> GmailHarness(config).let { scenario01SendSingleEmail(it, correspondent) }
        2 -> GmailHarness(config).let { scenario02ReceiveSingleEmail(it) }
        3 -> GmailHarness(config).let { scenario03ReplyThreading(it, correspondent) }
        4 -> GmailHarness(config).let { scenario04Attachment(it, correspondent) }
        5 -> GmailHarness(config).let { scenario05Html(it) }
        6 -> GmailHarness(config).let { scenario06PlainText(it, correspondent) }
        7 -> GmailHarness(config).let { scenario07Utf8(it, correspondent) }
        8 -> GmailHarness(config).let { scenario08PolishCharacters(it, correspondent) }
        9 -> GmailHarness(config).let { scenario09LargeAttachment(it, correspondent, oversized = false) }
        90 -> GmailHarness(config).let { scenario09LargeAttachment(it, correspondent, oversized = true) } // wariant >25MB
        10 -> GmailHarness(config).let { scenario10MultipleRecipients(it, correspondent, secondCorrespondent) }
        11 -> scenario11Cc()
        12 -> scenario12Bcc()
        13 -> GmailHarness(config).let { scenario13RepeatedPolling(it) }
        14 -> GmailHarness(config).let { scenario14ExactlyOnce(it) }
        15 -> scenario15RestartDuringReceive()
        16 -> scenario16RestartDuringSend()
        17 -> scenario17WrongCredentials()
        18 -> GmailHarness(config).let { scenario18Timeout(it, correspondent) }
        19 -> GmailHarness(config).let { scenario19ConnectionLoss(it) }
        20 -> GmailHarness(config).let { scenario20Reconnection(it) }
        else -> error("Nieznany scenariusz: $scenarioNumber (dozwolone: 1-20, 90 dla wariantu >25MB scenariusza 9)")
    }

    VerificationLog.scenarioResult(scenarioNumber, scenarioName(scenarioNumber), result.passed, result.details)
}

private fun scenarioName(number: Int): String = when (number) {
    1 -> "Wysłanie pojedynczego e-maila"
    2 -> "Odbiór pojedynczego e-maila"
    3 -> "Odpowiedź na wiadomość"
    4 -> "Załącznik"
    5 -> "HTML"
    6 -> "Plain Text"
    7 -> "UTF-8"
    8 -> "Polskie znaki"
    9 -> "Duży załącznik (20MB)"
    90 -> "Duży załącznik (>25MB, oczekiwane odrzucenie)"
    10 -> "Wiele odbiorców"
    11 -> "CC"
    12 -> "BCC"
    13 -> "Wielokrotne odpytywanie IMAP"
    14 -> "Exactly Once"
    15 -> "Restart aplikacji podczas odbioru"
    16 -> "Restart podczas wysyłki"
    17 -> "Błędne dane logowania"
    18 -> "Timeout"
    19 -> "Utrata połączenia"
    20 -> "Ponowne połączenie"
    else -> "nieznany"
}
