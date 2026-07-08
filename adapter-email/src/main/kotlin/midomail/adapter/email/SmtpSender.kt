package midomail.adapter.email

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import java.util.Properties

/**
 * Konfiguracja połączenia SMTP (20-Adapters/22-Adapter-Email.md, §8: SMTP Host/Port/SSL/STARTTLS,
 * Login, Hasło, Timeout).
 *
 * [timeoutMillis] — bez jawnego skonfigurowania, Jakarta Mail/Angus nie ograniczają czasu
 * połączenia/odczytu, przez co rzeczywista utrata sieci („czarna dziura", bez czystego zamknięcia
 * TCP) może pozostać niewykryta bardzo długo lub w ogóle (znalezione podczas ręcznej weryfikacji
 * produkcyjnej Fazy 2 na rzeczywistym Gmailu, docs/faza2-weryfikacja-gmail.md, scenariusz 20).
 */
data class SmtpConfig(
    val host: String,
    val port: Int,
    val ssl: Boolean,
    val starttls: Boolean,
    val username: String,
    val password: String,
    val timeoutMillis: Long = 30_000
)

/**
 * Wysyłka SMTP — rozróżnienie SSL/STARTTLS na podstawie portu i konfiguracji
 * (20-Adapters/22-Adapter-Email.md, §6).
 */
class SmtpSender(private val config: SmtpConfig) {

    val session: Session = createSession()

    fun send(mimeMessage: MimeMessage) {
        val transport = session.getTransport("smtp")
        try {
            transport.connect(config.host, config.port, config.username, config.password)
            transport.sendMessage(mimeMessage, mimeMessage.allRecipients)
        } finally {
            transport.close()
        }
    }

    private fun createSession(): Session {
        val properties = Properties()
        properties["mail.smtp.host"] = config.host
        properties["mail.smtp.port"] = config.port.toString()
        properties["mail.smtp.auth"] = "true"
        properties["mail.smtp.ssl.enable"] = config.ssl.toString()
        properties["mail.smtp.starttls.enable"] = config.starttls.toString()
        properties["mail.smtp.connectiontimeout"] = config.timeoutMillis.toString()
        properties["mail.smtp.timeout"] = config.timeoutMillis.toString()
        properties["mail.smtp.writetimeout"] = config.timeoutMillis.toString()
        return Session.getInstance(properties)
    }
}
