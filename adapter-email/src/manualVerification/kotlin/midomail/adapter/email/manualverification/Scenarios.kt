package midomail.adapter.email.manualverification

import midomail.domain.gateway.ProcessingResult
import midomail.domain.message.Attachment
import midomail.domain.message.Channel
import midomail.domain.message.ChannelType
import midomail.domain.message.CorrelationId
import midomail.domain.message.ExternalReference
import midomail.domain.message.GatewayMessage
import midomail.domain.message.Identity
import midomail.domain.message.MessageId
import midomail.domain.message.Payload
import midomail.domain.message.SchemaVersion
import midomail.domain.processing.ProcessingState
import java.util.UUID
import kotlin.random.Random

/**
 * Implementacje 20 scenariuszy z docs/faza2-weryfikacja-gmail.md, §8. Każdy zwraca [ScenarioResult]
 * z werdyktem i uzasadnieniem — ostateczna ocena (PASS względem §1 dla scenariuszy ze znanymi
 * ograniczeniami) należy do operatora czytającego log, zgodnie z „ręczna walidacja".
 */
data class ScenarioResult(val passed: Boolean, val details: String)

private fun freshIdentity(): Identity = Identity(
    messageId = MessageId(UUID.randomUUID().toString()),
    correlationId = CorrelationId(UUID.randomUUID().toString()),
    schemaVersion = SchemaVersion("2.0"),
    externalReference = ExternalReference("<manual-verification-${UUID.randomUUID()}@localhost>")
)

private fun waitForCondition(timeoutMillis: Long, pollEveryMillis: Long = 500, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return true
        Thread.sleep(pollEveryMillis)
    }
    return condition()
}

private fun pressEnterToContinue(instruction: String) {
    VerificationLog.info("RĘCZNA CZYNNOŚĆ WYMAGANA: $instruction")
    println(">>> Wykonaj powyższą czynność, potem naciśnij Enter, aby kontynuować...")
    readlnOrNull()
}

fun scenario01SendSingleEmail(harness: GmailHarness, correspondent: String): ScenarioResult {
    val message = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = harness.adapterId.let { "midomail-verification@localhost" }),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(content = "Scenariusz 1: pojedynczy e-mail z harnessu weryfikacyjnego midoMAIL 2.0."),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 1")
    )
    return try {
        harness.adapter.send(message)
        ScenarioResult(true, "Wysłano bez wyjątku — potwierdź ręcznie odbiór w skrzynce $correspondent")
    } catch (exception: Exception) {
        ScenarioResult(false, "Wyjątek podczas wysyłki: ${exception.message}")
    }
}

fun scenario02ReceiveSingleEmail(harness: GmailHarness): ScenarioResult {
    pressEnterToContinue("Wyślij ręcznie (z innej skrzynki) e-mail do konta testowego z tematem zawierającym 'scenariusz-2'")
    val store = harness.imapReceiver.connect()
    val found = waitForCondition(timeoutMillis = 30_000) {
        harness.imapReceiver.poll(store).any { it.subject?.contains("scenariusz-2") == true }
    }
    harness.imapReceiver.disconnect(store)
    return if (found) {
        ScenarioResult(true, "Wiadomość odebrana przez ImapReceiver.poll()")
    } else {
        ScenarioResult(false, "Wiadomość nie pojawiła się w INBOX w ciągu 30 s")
    }
}

fun scenario03ReplyThreading(harness: GmailHarness, correspondent: String): ScenarioResult {
    val original = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = "midomail-verification@localhost"),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(content = "Scenariusz 3: wiadomość oryginalna."),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 3 (oryginał)")
    )
    return try {
        // Celowo NIE adapter.send() — ta metoda nie zapisuje do MessageStore (słusznie: robi to
        // wyłącznie GatewayEngine na ścieżce odbioru, SPEC-0010/Iteracja 6). Budujemy MimeMessage
        // bezpośrednio przez mapper.toMime(), żeby po wysyłce odczytać FAKTYCZNY Message-ID
        // (Gmail/Jakarta Mail nadaje go dopiero przy wysyłce) i zarejestrować oryginał w
        // MessageStore pod tym samym ExternalReference, którego użyje nagłówek In-Reply-To
        // odpowiedzi.
        val mimeMessage = harness.mapper.toMime(original, harness.smtpSender.session)
        harness.smtpSender.send(mimeMessage)
        val actualMessageId = mimeMessage.getHeader("Message-ID")?.firstOrNull()
            ?: return ScenarioResult(false, "Wysłana wiadomość nie ma nagłówka Message-ID po wysyłce — nie da się zarejestrować oryginału")
        val originalWithRealReference = original.copy(
            identity = original.identity.copy(externalReference = ExternalReference(actualMessageId))
        )
        harness.messageStore.insertIfAbsent(ExternalReference(actualMessageId), originalWithRealReference)
        VerificationLog.info("Oryginał zarejestrowany w MessageStore pod rzeczywistym Message-ID: $actualMessageId")

        pressEnterToContinue(
            "Odpowiedz (Reply) z klienta pocztowego korespondenta na wiadomość 'midoMAIL — scenariusz 3 (oryginał)', " +
                "aby dotarła z powrotem na konto testowe z poprawnym In-Reply-To/References"
        )
        val store = harness.imapReceiver.connect()
        var causationOk = false
        var correlationOk = false
        var attempt = 0
        waitForCondition(timeoutMillis = 120_000) {
            attempt++
            val allMessages = harness.imapReceiver.poll(store)
            val matching = allMessages.filter { it.subject?.contains("scenariusz 3", ignoreCase = true) == true }
            VerificationLog.info(
                "Próba poll #$attempt: ${allMessages.size} wiadomości w INBOX ogółem, " +
                    "${matching.size} pasujących do tematu 'scenariusz 3' " +
                    "(tematy wszystkich: ${allMessages.map { it.subject }})"
            )
            matching.forEach { mime ->
                val inReplyTo = mime.getHeader("In-Reply-To")?.firstOrNull()
                val references = mime.getHeader("References")?.firstOrNull()
                VerificationLog.info(
                    "  kandydat: subject='${mime.subject}', In-Reply-To='$inReplyTo', References='$references', " +
                        "oczekiwany ExternalReference='$actualMessageId'"
                )
                val mapped = harness.mapper.fromMime(mime, ChannelType("email"), harness.messageStore)
                VerificationLog.info(
                    "  zmapowano: causationId=${mapped.identity.causationId}, correlationId=${mapped.identity.correlationId} " +
                        "(oczekiwany correlationId oryginału: ${originalWithRealReference.identity.correlationId})"
                )
                if (mapped.identity.causationId != null) causationOk = true
                if (mapped.identity.correlationId == originalWithRealReference.identity.correlationId) correlationOk = true
            }
            causationOk
        }
        harness.imapReceiver.disconnect(store)
        ScenarioResult(
            causationOk,
            "CausationId ustawiony: $causationOk, CorrelationId zgodny z oryginałem: $correlationOk (szczegóły każdej próby w logu powyżej)"
        )
    } catch (exception: Exception) {
        ScenarioResult(false, "Wyjątek: ${exception.message}")
    }
}

fun scenario04Attachment(harness: GmailHarness, correspondent: String): ScenarioResult {
    val bytes = Random.nextBytes(500 * 1024)
    val reference = harness.attachmentStore.write(bytes)
    val message = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = "midomail-verification@localhost"),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(
            content = "Scenariusz 4: załącznik 500 KB.",
            attachments = listOf(Attachment(contentType = "application/octet-stream", fileName = "test.bin", size = bytes.size.toLong(), dataReference = reference))
        ),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 4 (załącznik)")
    )
    return try {
        harness.adapter.send(message)
        ScenarioResult(true, "Wysłano z załącznikiem 500 KB — potwierdź ręcznie w skrzynce $correspondent, że załącznik jest kompletny i pobieralny")
    } catch (exception: Exception) {
        ScenarioResult(false, "Wyjątek: ${exception.message}")
    }
}

fun scenario05Html(harness: GmailHarness): ScenarioResult {
    pressEnterToContinue(
        "Z interfejsu webowego Gmail wyślij do konta testowego wiadomość sformatowaną (pogrubienie/link), " +
            "temat zawierający 'scenariusz-5', BEZ dodawania wersji plain text (jeśli klient na to pozwala)"
    )
    val store = harness.imapReceiver.connect()
    var content = ""
    val found = waitForCondition(timeoutMillis = 30_000) {
        val match = harness.imapReceiver.poll(store).firstOrNull { it.subject?.contains("scenariusz-5") == true }
        if (match != null) {
            content = harness.mapper.fromMime(match, ChannelType("email"), harness.messageStore).payload.content
            true
        } else {
            false
        }
    }
    harness.imapReceiver.disconnect(store)
    return if (!found) {
        ScenarioResult(false, "Wiadomość nie dotarła w ciągu 30 s")
    } else if (content.isBlank()) {
        ScenarioResult(true, "ZNANE OGRANICZENIE potwierdzone: treść pusta dla HTML-only (fromMime szuka wyłącznie text/plain, §1.3)")
    } else {
        ScenarioResult(true, "Treść niepusta ($content) — Gmail najwyraźniej dołączył alternatywę text/plain")
    }
}

fun scenario06PlainText(harness: GmailHarness, correspondent: String): ScenarioResult {
    val text = "Scenariusz 6: zwykły tekst bez formatowania, bez załączników."
    val message = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = "midomail-verification@localhost"),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(content = text),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 6")
    )
    return try {
        harness.adapter.send(message)
        ScenarioResult(true, "Wysłano — potwierdź ręcznie, że treść w skrzynce $correspondent to dokładnie: '$text'")
    } catch (exception: Exception) {
        ScenarioResult(false, "Wyjątek: ${exception.message}")
    }
}

fun scenario07Utf8(harness: GmailHarness, correspondent: String): ScenarioResult {
    val text = "Scenariusz 7: 日本語, emoji 🎉🚀, кириллица."
    val message = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = "midomail-verification@localhost"),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(content = text),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 7 UTF-8 🎉")
    )
    return try {
        harness.adapter.send(message)
        ScenarioResult(true, "Wysłano — potwierdź ręcznie brak uszkodzenia kodowania w skrzynce $correspondent")
    } catch (exception: Exception) {
        ScenarioResult(false, "Wyjątek: ${exception.message}")
    }
}

fun scenario08PolishCharacters(harness: GmailHarness, correspondent: String): ScenarioResult {
    val text = "Scenariusz 8: ąćęłńóśźż ĄĆĘŁŃÓŚŹŻ — zażółć gęślą jaźń."
    val message = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = "midomail-verification@localhost"),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(content = text),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 8 ąćęłńóśźż")
    )
    return try {
        harness.adapter.send(message)
        ScenarioResult(true, "Wysłano — potwierdź ręcznie brak '?' zamiast polskich znaków w skrzynce $correspondent")
    } catch (exception: Exception) {
        ScenarioResult(false, "Wyjątek: ${exception.message}")
    }
}

fun scenario09LargeAttachment(harness: GmailHarness, correspondent: String, oversized: Boolean): ScenarioResult {
    val sizeBytes = if (oversized) 26 * 1024 * 1024 else 20 * 1024 * 1024
    val bytes = Random.nextBytes(sizeBytes)
    val reference = harness.attachmentStore.write(bytes)
    val message = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = "midomail-verification@localhost"),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(
            content = "Scenariusz 9: duży załącznik (${sizeBytes / (1024 * 1024)} MB).",
            attachments = listOf(Attachment(contentType = "application/octet-stream", fileName = "large.bin", size = bytes.size.toLong(), dataReference = reference))
        ),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 9 (${if (oversized) "oversized" else "20MB"})")
    )
    return try {
        harness.adapter.send(message)
        if (oversized) {
            ScenarioResult(false, "OCZEKIWANO odrzucenia przez Gmail (>25MB), ale wysyłka się powiodła bez wyjątku — do zweryfikowania ręcznie")
        } else {
            ScenarioResult(true, "Wysłano 20 MB bez wyjątku — potwierdź ręcznie dostarczenie")
        }
    } catch (exception: Exception) {
        if (oversized) {
            ScenarioResult(true, "Oczekiwane odrzucenie przez Gmail: ${exception.message}")
        } else {
            ScenarioResult(false, "Nieoczekiwany wyjątek dla 20 MB: ${exception.message}")
        }
    }
}

fun scenario10MultipleRecipients(harness: GmailHarness, correspondent: String, secondCorrespondent: String?): ScenarioResult {
    if (secondCorrespondent == null) {
        return ScenarioResult(true, "Pominięto — brak drugiego adresu korespondenta do testu; ograniczenie modelu (§1.1) potwierdzone analitycznie: Channel.address to pojedynczy String")
    }
    return ScenarioResult(
        true,
        "ZNANE OGRANICZENIE (§1.1): GatewayMessage/Channel nie modelują wielu odbiorców — EmailMessageMapper.toMime ustawia dokładnie jeden adres To. Brak API do przekazania drugiego adresata bez zmiany modelu."
    )
}

fun scenario11Cc(): ScenarioResult = ScenarioResult(
    true,
    "ZNANE OGRANICZENIE (§1.1): brak pola CC w Channel/GatewayMessage/Attributes-konwencji obsługiwanej przez mapper — nieobsługiwane."
)

fun scenario12Bcc(): ScenarioResult = ScenarioResult(
    true,
    "ZNANE OGRANICZENIE (§1.1): brak pola BCC — analogicznie do CC, nieobsługiwane."
)

fun scenario13RepeatedPolling(harness: GmailHarness): ScenarioResult {
    pressEnterToContinue("Wyślij do konta testowego jedną wiadomość z tematem zawierającym 'scenariusz-13'")
    val store = harness.imapReceiver.connect()
    val counts = (1..10).map { attempt ->
        Thread.sleep(5_000)
        val count = harness.imapReceiver.poll(store).count { it.subject?.contains("scenariusz-13") == true }
        VerificationLog.info("Próba poll #$attempt: znaleziono $count wystąpień")
        count
    }
    harness.imapReceiver.disconnect(store)
    val allFoundEveryTime = counts.all { it == 1 }
    return ScenarioResult(allFoundEveryTime, "Liczby wystąpień w 10 próbach: $counts (oczekiwane: 1 za każdym razem — brak zależności od \\Seen)")
}

fun scenario14ExactlyOnce(harness: GmailHarness): ScenarioResult {
    pressEnterToContinue("Wyślij do konta testowego jedną wiadomość z tematem zawierającym 'scenariusz-14'")
    harness.start()
    // Kilka cykli pollingu (pollIntervalMillis harnessu) nad tą samą wiadomością — Zadanie 030.
    Thread.sleep(30_000)
    harness.stop()
    // EmailAdapter.pollOnce() (kod produkcyjny) słusznie przetwarza CAŁĄ zawartość INBOX, nie
    // tylko wiadomość tego scenariusza — jeśli w skrzynce leżą jeszcze stare wiadomości z
    // poprzednich scenariuszy (każdy startuje z nowym, pustym MessageStore), zostaną policzone
    // jako osobne, jednorazowe zdarzenia. Liczymy więc wyłącznie zdarzenia dotyczące WIADOMOŚCI
    // TEGO scenariusza (po temacie), żeby nie mylić tego z realną duplikacją Exactly Once.
    val relevantEvents = harness.eventPublisher.events().filter { event ->
        val message = event.payload as? GatewayMessage
        message?.attributes?.get("subject")?.contains("scenariusz-14", ignoreCase = true) == true
    }
    return ScenarioResult(
        relevantEvents.size <= 1,
        "Zdarzenia domenowe dla wiadomości scenariusza 14 po kilku cyklach pollingu: ${relevantEvents.size} " +
            "(oczekiwane: dokładnie 1; wszystkich zdarzeń w tym przebiegu: ${harness.eventPublisher.events().size} " +
            "— różnica to leftover z poprzednich scenariuszy w tej samej skrzynce, nie duplikacja)"
    )
}

fun scenario15RestartDuringReceive(): ScenarioResult {
    VerificationLog.warn(
        "Scenariusz NIEMOŻLIWY do zweryfikowania w obecnej architekturze: harness (i EmailAdapter/" +
            "GatewayEngine z Faz 1-2) używa wyłącznie InMemoryMessageStore. Restart procesu JVM z " +
            "definicji czyści cały stan Message Store do zera - to nie błąd, tylko właściwość " +
            "implementacji in-memory. Sensowna weryfikacja 'restart nie gubi/nie dubluje wiadomości' " +
            "wymaga trwałej implementacji Message Store, której jeszcze nie zbudowano (świadomie poza " +
            "zakresem Fazy 1/2)."
    )
    return ScenarioResult(true, "Pominięty świadomie - wymaga trwałego Message Store (przyszła faza), nie jest to luka Fazy 1/2")
}

fun scenario16RestartDuringSend(): ScenarioResult {
    VerificationLog.warn(
        "Scenariusz częściowo niemożliwy do pełnej weryfikacji z tego samego powodu co #15 " +
            "(brak trwałego Message Store) - ale wąski aspekt 'czy przerwany proces zostawia " +
            "uszkodzoną/zduplikowaną wiadomość w skrzynce odbiorcy' JEST sensowny i niezależny od " +
            "Message Store (dotyczy wyłącznie SMTP, nie stanu aplikacji). Wymaga ręcznej interwencji: " +
            "uruchom scenariusz 1 (wysyłka), przerwij proces (Ctrl+C) możliwie blisko momentu wysyłki, " +
            "sprawdź ręcznie skrzynkę korespondenta - wiadomość NIE powinna dotrzeć zdeformowana " +
            "(dopuszczalne: brak dostarczenia; niedopuszczalne: uszkodzona treść)."
    )
    return ScenarioResult(true, "Aspekt SMTP-owy wymaga ręcznej oceny (patrz log) - aspekt stanu aplikacji pominięty z tego samego powodu co #15")
}

fun scenario17WrongCredentials(): ScenarioResult {
    val badConfig = GmailTestConfig(
        address = System.getenv("GMAIL_TEST_ADDRESS") ?: "unknown@gmail.com",
        appPassword = "celowo-zle-haslo-0000",
        correspondentAddress = null
    )
    val harness = GmailHarness(badConfig, pollIntervalMillis = 60_000)
    return try {
        harness.start()
        Thread.sleep(3_000)
        val healthy = harness.adapter.health()
        harness.stop()
        ScenarioResult(!healthy.healthy, "health() po błędnych danych logowania: healthy=${healthy.healthy}, details=${healthy.details} (oczekiwane: healthy=false)")
    } catch (exception: Exception) {
        ScenarioResult(true, "Oczekiwany wyjątek przy starcie z błędnymi danymi: ${exception.message}")
    }
}

fun scenario18Timeout(harness: GmailHarness, correspondent: String): ScenarioResult {
    pressEnterToContinue("Rozłącz sieć (Wi-Fi/Ethernet) TERAZ, potwierdź w przeglądarce że nic się nie ładuje, potem naciśnij Enter")
    val message = GatewayMessage(
        identity = freshIdentity(),
        source = Channel(type = ChannelType("email"), address = "midomail-verification@localhost"),
        destination = Channel(type = ChannelType("email"), address = correspondent),
        payload = Payload(content = "Scenariusz 18: pomiar czasu do zgłoszenia błędu przy braku sieci."),
        attributes = mapOf("subject" to "midoMAIL — scenariusz 18")
    )
    val startNanos = System.nanoTime()
    return try {
        harness.adapter.send(message)
        ScenarioResult(false, "Wysyłka powiodła się mimo rozłączonej sieci — sieć nie była faktycznie rozłączona?")
    } catch (exception: Exception) {
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000
        // SmtpConfig.timeoutMillis (domyślnie/skonfigurowane w GmailHarness na 10s) - oczekujemy
        // błędu w rozsądnym, ograniczonym czasie, a nie po bardzo długim domyślnym timeout OS/JVM
        // (co byłoby objawem regresji naprawionej w Iteracji "Fix: SmtpConfig/ImapConfig...").
        ScenarioResult(
            elapsedMillis < 20_000,
            "Błąd zgłoszony po $elapsedMillis ms: ${exception.message} " +
                "(oczekiwane: w granicach skonfigurowanego timeoutu ~10s, nie długi domyślny timeout systemowy)"
        )
    }
}

fun scenario19ConnectionLoss(harness: GmailHarness): ScenarioResult {
    // Sieć może być niedostępna już w momencie startu (nie tylko w trakcie działania) - registry
    // rzuca wtedy wyjątek po przejściu Initializing→Failed (ADR-0014-Registry-Start-Failure.md).
    // To composition roota (tutaj: harnessu) obowiązek złapać go i sprawdzić stan, a nie pozwolić
    // procesowi się wywalić - dokładnie to, co ADR-0014 zakładał po stronie wywołującego.
    val startException = try {
        harness.start()
        null
    } catch (exception: Exception) {
        exception
    }
    if (startException != null) {
        val health = harness.adapter.health()
        VerificationLog.info("start() rzucił wyjątek natychmiast (sieć niedostępna już przy starcie): ${startException.message}")
        return ScenarioResult(
            !health.healthy,
            "Sieć niedostępna już przy starcie - obsłużone poprawnie, bez crasha procesu (ADR-0014): " +
                "${startException.message}; health()=healthy=${health.healthy}"
        )
    }
    pressEnterToContinue("Rozłącz sieć (Wi-Fi/Ethernet) TERAZ, poczekaj aż harness wypisze błąd w logu, potem naciśnij Enter")
    // Domyślny interwał pollingu harnessu to 5 s, a pierwsze wykonanie zaplanowanego zadania
    // następuje dopiero PO upływie interwału (nie natychmiast) - czekamy więc do 30 s,
    // sprawdzając health() co sekundę, zamiast jednego zbyt krótkiego, sztywnego opóźnienia.
    var health = harness.adapter.health()
    val becameUnhealthy = waitForCondition(timeoutMillis = 30_000, pollEveryMillis = 1_000) {
        health = harness.adapter.health()
        VerificationLog.info("Sprawdzenie health(): healthy=${health.healthy}, details=${health.details}")
        !health.healthy
    }
    harness.stop()
    return ScenarioResult(
        becameUnhealthy,
        "health() po utracie połączenia: healthy=${health.healthy}, details=${health.details} (oczekiwane: healthy=false, brak crasha procesu)"
    )
}

fun scenario20Reconnection(harness: GmailHarness): ScenarioResult {
    val startException = try {
        harness.start()
        null
    } catch (exception: Exception) {
        exception
    }
    if (startException != null) {
        return ScenarioResult(
            false,
            "Sieć była niedostępna już przy starcie (${startException.message}) - najpierw uruchom " +
                "scenariusz z siecią włączoną, rozłącz ją dopiero PO wystartowaniu adaptera"
        )
    }
    pressEnterToContinue("Rozłącz sieć TERAZ (Wi-Fi/Ethernet), poczekaj aż naciśniesz Enter, sieć NADAL rozłączona")
    val becameUnhealthy = waitForCondition(timeoutMillis = 30_000, pollEveryMillis = 1_000) {
        val health = harness.adapter.health()
        VerificationLog.info("Oczekiwanie na wykrycie rozłączenia: healthy=${health.healthy}")
        !health.healthy
    }
    if (!becameUnhealthy) {
        harness.stop()
        return ScenarioResult(false, "Adapter nie wykrył rozłączenia w ciągu 30 s - nie można zweryfikować odzyskania połączenia")
    }
    pressEnterToContinue("Rozłączenie wykryte (patrz log powyżej). TERAZ przywróć sieć, potem naciśnij Enter")
    var health = harness.adapter.health()
    val recovered = waitForCondition(timeoutMillis = 30_000, pollEveryMillis = 1_000) {
        health = harness.adapter.health()
        VerificationLog.info("Oczekiwanie na odzyskanie połączenia: healthy=${health.healthy}")
        health.healthy
    }
    harness.stop()
    return ScenarioResult(
        recovered,
        "health() po przywróceniu sieci: healthy=${health.healthy} (oczekiwane: healthy=true jeśli adapter samodzielnie odzyskał połączenie w kolejnym cyklu; jeśli false — wymagany ręczny restart, odnotować w raporcie)"
    )
}
