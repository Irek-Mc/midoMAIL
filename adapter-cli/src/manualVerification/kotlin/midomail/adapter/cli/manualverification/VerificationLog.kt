package midomail.adapter.cli.manualverification

import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Log wykonania harnessu (docs/faza5-weryfikacja.md) — ten sam wzorzec co `:adapter-rest`.
 */
object VerificationLog {
    private val logFile = File("docs/faza5-cli-weryfikacja-logi.txt").also {
        Files.createDirectories(it.absoluteFile.parentFile.toPath())
    }

    fun line(level: String, message: String) {
        val entry = "[${Instant.now()}] [$level] $message"
        println(entry)
        logFile.appendText(entry + "\n")
    }

    fun info(message: String) = line("INFO", message)

    fun scenarioStart(number: Int, name: String) {
        line("SCENARIUSZ", "=== #$number: $name — START ===")
    }

    fun scenarioResult(number: Int, name: String, passed: Boolean, details: String) {
        line("SCENARIUSZ", "=== #$number: $name — ${if (passed) "PASS" else "FAIL"} — $details ===")
    }
}
