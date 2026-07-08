# SPEC-0010 — Plugin SDK Contract

**Status:** Accepted
**Powiązane dokumenty:** 10-Core/19-Plugin-SDK.md, 10-Core/14-Registry-Adapterow.md, SPEC-0006-Adapter-Contract.md

---

# Cel

Dokument stanowi techniczną, implementowalną specyfikację Plugin SDK. 10-Core/19-Plugin-SDK.md opisuje ideę i cykl życia pluginu na poziomie pojęciowym; niniejszy dokument definiuje konkretny interfejs (w pseudokodzie Kotlin), mechanizm deklaracji możliwości, przepływ rejestracji i konfiguracji oraz sposób wstrzykiwania zależności — bez tego każdy adapter byłby implementowany inaczej, a Registry Adapterów nie miałby jednego kontraktu do wyegzekwowania.

---

# Interfejs Adapter

```kotlin
interface Adapter {
    val adapterId: AdapterId
    val adapterVersion: String

    fun supportedChannels(): Set<Channel>
    fun supportedCapabilities(): Set<Capability>

    fun start()
    fun stop()
    fun health(): HealthStatus
    fun metrics(): Metrics

    // Kierunek wyjściowy: Gateway Engine wywołuje tę metodę, aby przekazać
    // komunikat do transportu. Adapter nie podejmuje decyzji routingowych —
    // otrzymuje już wyznaczony komunikat.
    fun send(message: GatewayMessage)
}
```

To jest implementowalna forma „Minimalnego kontraktu" z SPEC-0006-Adapter-Contract.md. Kierunek wejściowy (odbiór komunikatu z transportu) nie jest metodą wywoływaną NA adapterze — adapter sam inicjuje dostarczenie komunikatu do Gateway poprzez port `GatewayInbound` otrzymany przy tworzeniu (patrz §Porty przekazywane adapterowi). Powód: różne transporty odbierają komunikaty w fundamentalnie różny sposób (callback systemowy jak BroadcastReceiver, polling jak IMAP przez Scheduler, webhook HTTP) — SDK nie narzuca jednego mechanizmu odbioru, tylko jednolity sposób dostarczenia odebranego komunikatu do rdzenia.

---

# Model Capability

```kotlin
enum class Capability {
    SUPPORTS_ATTACHMENTS,
    SUPPORTS_MMS,
    SUPPORTS_MULTIPART
    // nowa wartość nie wymaga zmiany interfejsu Adapter ani Gateway Engine —
    // Routing Engine sprawdza przynależność do zbioru generycznie (Set.contains),
    // nigdy nie odczytuje wartości przez rzutowanie na konkretny typ transportu.
}
```

Adapter deklaruje swoje możliwości zwracając `Set<Capability>` z `supportedCapabilities()`. Routing Engine odczytuje ten zbiór przy wyznaczaniu adaptera docelowego (SPEC-0007-Routing-Contract.md) — komunikat z niepustą listą Attachments nie jest kierowany do adaptera bez `SUPPORTS_ATTACHMENTS` (SPEC-0001-GatewayMessage.md, §Payload).

---

# Porty przekazywane adapterowi

```kotlin
data class AdapterPorts(
    val gatewayInbound: GatewayInbound,
    val messageStore: MessageStore,
    val logger: Logger,
    val healthReporter: HealthReporter,
    val attachmentStore: AttachmentStore
)

interface GatewayInbound {
    // Adapter wywołuje tę metodę, gdy odbierze nowy komunikat z transportu.
    // Rejestracja ExternalReference i weryfikacja Exactly Once (SPEC-0008)
    // zachodzi wewnątrz tej operacji, zanim komunikat trafi do Routing Engine.
    fun receive(message: GatewayMessage): ProcessingResult
}

interface Logger {
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}

// Kierunek odwrotny do HealthProvider (10-Core/18-Porty.md) - HealthProvider jest odpytywany
// ("pull"), HealthReporter służy adapterowi do aktywnego zgłoszenia zmiany własnego stanu
// ("push"), np. natychmiast po utracie połączenia IMAP, bez czekania na najbliższe odpytanie.
interface HealthReporter {
    fun report(adapterId: AdapterId, status: HealthStatus)
}
```

`messageStore` jest przekazywany adapterowi w zakresie **wyłącznie do odczytu** wymaganym przez wątkowanie (np. Adapter Email wywołujący `findByExternalReference` przy ustalaniu oryginalnej wiadomości dla odpowiedzi, patrz 20-Adapters/22-Adapter-Email.md, §6). Adapter nigdy nie zapisuje bezpośrednio do Message Store — zapis odbywa się wyłącznie wewnątrz `GatewayInbound.receive()` i Gateway Engine.

`logger` jest niezależny od konkretnej biblioteki logowania — implementacja platformowa (np. SLF4J na JVM, Android Log na Androidzie) znajduje się poza `:domain`. `healthReporter` wykorzystuje `HealthStatus` z SPEC-0015-Adapter-Observability-Contract.md.

**Amendment (Faza 2, Iteracja 2.6):** `attachmentStore` dodany do `AdapterPorts` — adapter obsługujący załączniki (np. Adapter Email, 20-Adapters/22-Adapter-Email.md, §7) musi rozwiązywać `DataReference` do rzeczywistych bajtów przy mapowaniu Payload ↔ transport. Port zdefiniowany przez ADR-0013-Attachment-Store.md/SPEC-0016-Attachment-Store-Contract.md, wprowadzony po pierwotnym zamrożeniu `AdapterPorts` (Iteracja 2.1) — jawna, udokumentowana zmiana zamrożonego kontraktu, analogicznie do ADR-0012 rozszerzającego `Channel` w Fazie 1.

---

# Konfiguracja adaptera

```kotlin
interface AdapterFactory {
    fun create(configuration: AdapterConfiguration, ports: AdapterPorts): Adapter
}
```

`AdapterConfiguration` odpowiada modelowi z SPEC-0005-Configuration-Model.md, sekcja Adapters — jeden zestaw parametrów na zarejestrowany adapter (np. host/port/login dla Adaptera Email), zwalidowany przed przekazaniem do fabryki.

---

# Przepływ rejestracji i konfiguracji

1. Kompozycja startowa (composition root — np. punkt wejścia aplikacji na danej platformie) odczytuje `AdapterConfiguration` dla każdego skonfigurowanego adaptera (SPEC-0005-Configuration-Model.md).
2. Composition root wywołuje `AdapterFactory.create(configuration, ports)`, budując `AdapterPorts` z już zainicjalizowanych implementacji portów Core.
3. Powstała instancja `Adapter` jest przekazywana do `Registry.register(adapter)`.
4. Registry przeprowadza adapter przez stany Registered → Initializing → Ready (14-Registry-Adapterow.md, §4), wywołując `adapter.start()` podczas przejścia Initializing → Ready.
5. Każde przejście stanu jest publikowane jako zdarzenie domenowe (10-Core/15-Event-Bus.md).
6. Zatrzymanie odbywa się symetrycznie: Registry wywołuje `adapter.stop()` podczas przejścia Stopping → Stopped.

Ten przepływ jest identyczny dla każdego adaptera niezależnie od transportu — nowy adapter wymaga wyłącznie własnej implementacji `Adapter`/`AdapterFactory` i wpisu konfiguracyjnego, bez zmian w Registry, Gateway Engine ani Routing Engine (10-Core/14-Registry-Adapterow.md, §6).

---

# Mechanizm DI

Zgodnie z 50-Quality/51-Standard-kodowania.md, §5 (Zasady projektowe: „Zależności są przekazywane przez konstruktor", „Architektura nie wymaga frameworków Dependency Injection") — zastosowane konkretnie do adapterów:

- `AdapterFactory.create()` jest jedynym miejscem tworzenia instancji adaptera; wszystkie zależności (porty, konfiguracja) są przekazywane jako parametry tej metody, nie odczytywane samodzielnie przez adapter (np. przez singleton, service locator albo refleksję).
- Composition root jest jedynym miejscem, które zna wszystkie konkretne implementacje portów i jawnie je łączy — żaden adapter nie tworzy własnych zależności ani nie sięga po nie poza tym, co otrzymał w `AdapterPorts`.
- Brak kontenera DI, brak adnotacji wstrzykiwania zależności, brak refleksji do budowy grafu zależności — cały graf jest jawny i widoczny w kodzie composition root.

---

# Cykl życia pluginu — mapowanie na interfejs

| Etap (10-Core/19-Plugin-SDK.md, §6) | Operacja na interfejsie |
|---|---|
| Wykrycie | Composition root odczytuje konfigurację adaptera |
| Weryfikacja zgodności | Walidacja `AdapterConfiguration` przed wywołaniem `create()` |
| Rejestracja | `Registry.register(adapter)` |
| Inicjalizacja | `adapter.start()` |
| Uruchomienie | Stan Ready w Registry |
| Monitorowanie | `adapter.health()`, `adapter.metrics()` |
| Zatrzymanie | `adapter.stop()` |
| Wyrejestrowanie | `Registry.unregister(adapterId)` |

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/14-Registry-Adapterow.md
- 10-Core/19-Plugin-SDK.md
- 20-Adapters/22-Adapter-Email.md
- 50-Quality/51-Standard-kodowania.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0007-Routing-Contract.md
- 91-Specification/SPEC-0008-Exactly-Once-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0015-Adapter-Observability-Contract.md
