package midomail.adapter.email

import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.MimeMessage
import java.util.Properties

/**
 * Konfiguracja połączenia IMAP (20-Adapters/22-Adapter-Email.md, §8: IMAP Host/Port/IMAPS/
 * STARTTLS, Login, Hasło, Folder synchronizacji, Timeout).
 *
 * [timeoutMillis] — bez jawnego skonfigurowania, Jakarta Mail/Angus nie ograniczają czasu
 * połączenia/odczytu, przez co rzeczywista utrata sieci („czarna dziura", bez czystego zamknięcia
 * TCP) może pozostać niewykryta bardzo długo lub w ogóle (znalezione podczas ręcznej weryfikacji
 * produkcyjnej Fazy 2 na rzeczywistym Gmailu, docs/faza2-weryfikacja-gmail.md, scenariusz 20).
 */
data class ImapConfig(
    val host: String,
    val port: Int,
    val imaps: Boolean,
    val starttls: Boolean,
    val username: String,
    val password: String,
    val folder: String,
    val timeoutMillis: Long = 30_000
)

/**
 * Odbiór IMAP (20-Adapters/22-Adapter-Email.md, §6) — rozróżnienie IMAPS/STARTTLS na podstawie
 * portu i konfiguracji.
 *
 * **Jawnie: brak polegania na fladze `\Seen`.** Gmail w `READ_ONLY` jej nie ustawia, więc
 * deduplikacja nigdy nie może opierać się na stanie tej flagi — [poll] zwraca wszystkie
 * wiadomości w folderze synchronizacji przy każdym wywołaniu, niezależnie od `\Seen`.
 * Deduplikacja odbywa się wyłącznie przez `ExternalReference` (Message-ID) w Exactly Once
 * (10-Core/17-Exactly-Once.md) — nie w tej klasie.
 *
 * Folder pozostaje otwarty (`READ_ONLY`) między kolejnymi wywołaniami [poll] — zamykanie go po
 * każdym odczycie unieważnia leniwie wczytywane pola wcześniej zwróconych `MimeMessage`
 * ([jakarta.mail.FolderClosedException]). Zamykany jest wyłącznie przez [disconnect], wywoływane
 * przy zatrzymaniu adaptera.
 */
class ImapReceiver(private val config: ImapConfig) {

    private var folder: Folder? = null

    val session: Session = createSession()

    fun connect(): Store {
        val store = session.getStore("imap")
        store.connect(config.host, config.port, config.username, config.password)
        return store
    }

    private fun createSession(): Session {
        val properties = Properties()
        properties["mail.imap.ssl.enable"] = config.imaps.toString()
        properties["mail.imap.starttls.enable"] = config.starttls.toString()
        properties["mail.imap.connectiontimeout"] = config.timeoutMillis.toString()
        properties["mail.imap.timeout"] = config.timeoutMillis.toString()
        properties["mail.imap.writetimeout"] = config.timeoutMillis.toString()
        return Session.getInstance(properties)
    }

    fun poll(store: Store): List<MimeMessage> {
        val openFolder = folder ?: store.getFolder(config.folder).also {
            it.open(Folder.READ_ONLY)
            folder = it
        }
        // Wymusza żywe zapytanie do serwera (STATUS/EXAMINE), zamiast polegać wyłącznie na
        // lokalnie zbuforowanej liczbie wiadomości — w połączeniu z mail.imap.timeout powyżej,
        // to jest rzeczywisty mechanizm wykrywania martwego połączenia w rozsądnym, ograniczonym
        // czasie.
        openFolder.messageCount
        return openFolder.messages.filterIsInstance<MimeMessage>()
    }

    fun disconnect(store: Store) {
        folder?.close(false)
        folder = null
        store.close()
    }
}
