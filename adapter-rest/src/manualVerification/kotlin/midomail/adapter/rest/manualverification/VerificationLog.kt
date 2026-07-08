package midomail.adapter.rest.manualverification

import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Log wykonania harnessu (docs/faza5-weryfikacja.md) — ten sam wzorzec co
 * `:adapter-email` (Faza 2)/`:notification-webhook` (Faza 4): plik, nie konsola, żeby zachować
 * pełny ślad do raportu końcowego.
 */
object VerificationLog {
    private val logFile = File("docs/faza5-rest-weryfikacja-logi.txt").also {
        Files.createDirectories(it.absoluteFile.parentFile.toPath())
    }

    fun line(level: String, message: String) {
        val entry = "[${Instant.now()}] [$level] $message"
        println(entry)
        logFile.appendText(entry + "\n")
    }

    fun info(message: String) = line("INFO", message)
    fun error(message: String) = line("ERROR", message)

    fun scenarioStart(number: Int, name: String) {
        line("SCENARIUSZ", "=== #$number: $name — START ===")
    }

    fun scenarioResult(number: Int, name: String, passed: Boolean, details: String) {
        line("SCENARIUSZ", "=== #$number: $name — ${if (passed) "PASS" else "FAIL"} — $details ===")
    }
}
