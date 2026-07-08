# SPEC-0026 — WebSocket Adapter Contract

**Status:** Accepted
**Powiązane dokumenty:** 20-Adapters/24-Adapter-WebSocket.md, 91-Specification/SPEC-0001-GatewayMessage.md, 91-Specification/SPEC-0005-Configuration-Model.md, 91-Specification/SPEC-0006-Adapter-Contract.md, 91-Specification/SPEC-0010-Plugin-SDK-Contract.md, 90-ADR/ADR-0010-Model-Channel.md

---

# Cel

`50-Quality/55-Roadmap.md` §9 (Faza 7): „Adapter WebSocket jako kolejny transport po REST/CLI." `24-Adapter-WebSocket.md` opisuje odpowiedzialność adaptera na wysokim poziomie („ustanawianie i utrzymywanie połączeń WebSocket", „integracja Communication Gateway z systemami wymagającymi dwukierunkowej komunikacji w czasie rzeczywistym"), ale nie rozstrzyga kierunku (serwer vs klient) ani kształtu konfiguracji. Ten dokument zamyka tę lukę, zanim powstanie jakikolwiek kod (Iteracja 7.4).

---

# 1. Kierunek połączenia — Gateway jako KLIENT

`24-Adapter-WebSocket.md` §4 („Adapter odpowiada za inicjalizację, utrzymanie oraz zamykanie sesji WebSocket") jest zgodne z obydwoma kierunkami dosłownie, ale §2 („integracja z systemami wymagającymi dwukierunkowej komunikacji") sugeruje integrację z ZEWNĘTRZNYM systemem czasu rzeczywistego — analogicznie do tego, jak Adapter Email jest klientem serwera SMTP/IMAP (20-Adapters/22-Adapter-Email.md), a nie serwerem przyjmującym połączenia pocztowe.

**Decyzja:** Adapter WebSocket łączy się WYCHODZĄCO do skonfigurowanego adresu URL zewnętrznej usługi WebSocket. Nie przyjmuje połączeń przychodzących. Formalizowane w ADR-0035 (Iteracja 7.1).

**Konsekwencja zależności:** JDK 11+ dostarcza `java.net.http.WebSocket` — klienta WebSocket wbudowanego w standardową bibliotekę, bez potrzeby żadnej zewnętrznej zależności. Gdyby wymagany był tryb serwerowy (przyjmowanie połączeń), `com.sun.net.httpserver.HttpServer` (już używany przez `AdminHttpServer`/`StaticFileServer`) NIE wspiera natywnie protokołu WebSocket (RFC 6455 handshake+ramki) — wymagałoby to albo własnej implementacji protokołu, albo czwartego świadomego wyjątku od minimalizacji zależności (po jakarta.mail/kotlinx.serialization/kaml).

---

# 2. Mapowanie na kontrakt `Adapter` (SPEC-0006/SPEC-0010)

Adapter WebSocket implementuje dokładnie ten sam, zamrożony interfejs `Adapter`/`AdapterFactory`/`AdapterPorts` co Email/GSM/REST — zero zmian w tym kontrakcie:

| Element `Adapter` | Zachowanie WebSocket |
|---|---|
| `adapterId`/`adapterVersion` | Jak w pozostałych adapterach |
| `supportedChannels()` | `setOf(Channel(type = ChannelType("websocket")))` — `"websocket"` był już przewidziany jako przykładowa wartość `ChannelType` od Fazy 1 (ADR-0010-Model-Channel.md, SPEC-0001-GatewayMessage.md §ChannelType); `ChannelType` to prosty wrapper na string, nie zamknięty enum — brak zmiany domenowej |
| `supportedCapabilities()` | Zbiór pusty w pierwszej wersji — brak dodatkowych możliwości (bez załączników/MMS/multipart) |
| `health()` | `healthy = true` gdy połączenie WebSocket aktywne; `details` opisuje ostatni błąd/rozłączenie |
| `metrics()` | `messagesSent`/`messagesReceived`/`errorCount`/`throttledCount` jak w pozostałych adapterach |
| `send(message: GatewayMessage)` | Serializuje `payload.content` jako ramkę tekstową WebSocket i wysyła przez aktywne połączenie; jeśli połączenie nieaktywne — błąd zgłoszony przez `EventPublisher`/`Logger` (ten sam wzorzec co pozostałe adaptery), nie próba automatycznego reconnect w trakcie `send()` |
| Odbiór przychodzący | Adapter inicjuje `GatewayInbound.receive()` sam, z callbacku `Listener.onText()` klienta `java.net.http.WebSocket` — nie metoda wywoływana NA adapterze (identyczny wzorzec jak IMAP polling w Adapterze Email) |
| `ExternalReference` | Brak naturalnego identyfikatora w gołym protokole WebSocket (w przeciwieństwie do `Message-ID` z SMTP czy numeru PDU z SMS) — wygenerowany przez adapter jako UUID przy odbiorze każdej ramki tekstowej, udokumentowane jako świadome uproszczenie (SPEC-0006 §ExternalReference wymaga wartości „naturalnej", gdy istnieje; gdy protokół jej nie dostarcza, adapter generuje syntetyczną) |

---

# 3. `WebSocketAdapterConfiguration`

Analogicznie do `EmailAdapterConfiguration`/`GsmAdapterConfiguration` (`AdapterConfiguration` marker, SPEC-0005 §Adapters):

```kotlin
data class WebSocketAdapterConfiguration(
    val url: String,
    val reconnectPolicy: ReconnectPolicy,
    val heartbeatIntervalMillis: Long
) : AdapterConfiguration

data class ReconnectPolicy(
    val maxAttempts: Int,
    val backoffMillis: Long
)
```

Odpowiednik w `SPEC-0005-Configuration-Model.md` §Adapters (nowa pozycja `type: "websocket"`, analogicznie do `email`/`gsm`):

```yaml
  - adapterId: "websocket-primary"
    type: "websocket"
    enabled: true
    config:
      url: "wss://example.com/realtime"
      reconnect:
        maxAttempts: 5
        backoffMs: 2000
      heartbeatIntervalMs: 30000
```

Sekcja `security.secretStore` (SPEC-0005 §5, wartości `android-keystore | env | file`) już przewiduje `env`/`file` — istotne dla `SecretStore` platformy JVM (Iteracja 7.6), niezależnie od tego adaptera.

---

# 4. Rejestr punktów stop-and-ask (Faza 7)

- **SA-16 (kierunek WebSocket):** rozstrzygnięte w §1 tego dokumentu — klient, nie serwer. Jeśli w trakcie implementacji (Iteracja 7.4) okaże się, że wymagany jest tryb serwerowy — STOP, nowy ADR przed kodem.
- **SA-17 (konfiguracja WebSocket):** rozstrzygnięte w §3 — `WebSocketAdapterConfiguration`/`ReconnectPolicy`, nowa pozycja `type: "websocket"` w SPEC-0005 §Adapters.
- **SA-18 (`:platform-jvm` — SecretStore/SchedulerProvider/ResourceMonitor JVM):** poza zakresem tego dokumentu — rozstrzygane w ADR-0036 (Iteracja 7.2)/Iteracji 7.6.
- **SA-19 (routing rules dla `:platform-jvm`):** poza zakresem tego dokumentu — rozstrzygane w ADR-0036 (Iteracja 7.2).

---

# 5. Co pozostaje poza zakresem tego adaptera

- Tryb serwerowy (przyjmowanie połączeń przychodzących) — §1.
- Załączniki binarne przez WebSocket (`supportedCapabilities()` pusty w pierwszej wersji).
- Uwierzytelnianie połączenia WebSocket (np. token w URL/nagłówku) — jeśli okaże się potrzebne przy weryfikacji end-to-end (Iteracja 7.5) z prawdziwym serwerem testowym, zostanie dodane jako amendment do `WebSocketAdapterConfiguration`, udokumentowany w Traceability Matrix, nie cichy.

---

# Dokumenty powiązane
- 20-Adapters/24-Adapter-WebSocket.md
- 90-ADR/ADR-0010-Model-Channel.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
