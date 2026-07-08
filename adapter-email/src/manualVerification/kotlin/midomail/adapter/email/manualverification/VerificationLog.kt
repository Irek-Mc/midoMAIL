package midomail.adapter.email.manualverification

import midomail.domain.adapter.AdapterId
import midomail.domain.adapter.HealthReporter
import midomail.domain.adapter.HealthStatus
import midomail.domain.adapter.Logger
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Log wykonania harnessu (docs/faza2-weryfikacja-gmail.md, §9) — plik, nie konsola, żeby
 * zachować pełny ślad do raportu końcowego. Nigdy nie zapisuje danych logowania.
 *
 * Ścieżka jest niezależna od katalogu roboczego procesu (`user.dir`) — zadanie Gradle
 * `runManualVerification` ustawia `workingDir` na katalog główny projektu, ale ten kod jest
 * defensywny również przy uruchomieniu spoza Gradle: katalog docelowy jest tworzony
 * automatycznie ([Files.createDirectories]), nie zakłada się jego istnienia.
 */
object VerificationLog {
    private val logFile = File("docs/faza2-gmail-weryfikacja-logi.txt").also {
        Files.createDirectories(it.absoluteFile.parentFile.toPath())
    }

    fun line(level: String, message: String) {
        val entry = "[${Instant.now()}] [$level] $message"
        println(entry)
        logFile.appendText(entry + "\n")
    }

    fun info(message: String) = line("INFO", message)
    fun warn(message: String) = line("WARN", message)
    fun error(message: String) = line("ERROR", message)

    fun scenarioStart(number: Int, name: String) {
        line("SCENARIUSZ", "=== #$number: $name — START ===")
    }

    fun scenarioResult(number: Int, name: String, passed: Boolean, details: String) {
        line("SCENARIUSZ", "=== #$number: $name — ${if (passed) "PASS" else "FAIL"} — $details ===")
    }
}

/** Adapter [Logger] (port z SPEC-0010) piszący do [VerificationLog]. */
val fileLogger: Logger = object : Logger {
    override fun info(message: String) = VerificationLog.info(message)
    override fun warn(message: String, throwable: Throwable?) =
        VerificationLog.warn("$message${throwable?.let { " — ${it.message}" } ?: ""}")
    override fun error(message: String, throwable: Throwable?) =
        VerificationLog.error("$message${throwable?.let { " — ${it.message}" } ?: ""}")
}

/** [HealthReporter] rejestrujący zgłoszenia stanu w logu. */
val loggingHealthReporter: HealthReporter = object : HealthReporter {
    override fun report(adapterId: AdapterId, status: HealthStatus) {
        VerificationLog.info("HealthReporter: adapter=${adapterId.value} healthy=${status.healthy} details=${status.details}")
    }
}
