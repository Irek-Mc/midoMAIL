# SPEC-0005 — Configuration Model

**Status:** Accepted
**Powiązany dokument:** 30-Infrastructure/30-Konfiguracja.md

---

# Cel

Dokument definiuje kanoniczny model konfiguracji platformy midoMAIL 2.0 oraz kontrakty wymiany konfiguracji pomiędzy komponentami Communication Gateway.

---

# Założenia

- Konfiguracja jest niezależna od platformy uruchomieniowej.
- Konfiguracja jest wersjonowana.
- Dane konfiguracyjne są walidowane przed użyciem.
- Sekrety są przechowywane poza jawną konfiguracją lub oznaczane jako poufne.

---

# Struktura logiczna

Model konfiguracji składa się z sekcji:

1. Gateway
2. Routing
3. Adapters (w tym opcjonalne Rate Limiting per adapter — SPEC-0011-Rate-Limiting-Contract.md)
4. Scheduler
5. Security
6. Monitoring
7. Platform
8. Message Store (retencja — SPEC-0009-Message-Store-Contract.md)
9. Notifications (kanały, routing wg poziomu, eskalacja — 30-Infrastructure/38-Powiadomienia.md)

---

# Wymagania kontraktu

Każda sekcja konfiguracji powinna posiadać:

- identyfikator,
- wersję,
- zakres obowiązywania,
- reguły walidacji,
- domyślne wartości (jeżeli dopuszczalne).

---

# Zasady zmian

Zmiany konfiguracji powinny być śledzone, możliwe do audytu oraz umożliwiać bezpieczne odtworzenie poprzedniej wersji.

---

# Format pliku

Kanonicznym formatem konfiguracji jest **YAML** (ADR-0004-Format-Konfiguracji.md). Model w pamięci jest niezależny od formatu pliku; parser YAML jest jedynym miejscem znającym szczegóły składni.

---

# Przykładowy plik konfiguracyjny

```yaml
version: "2.0"

gateway:
  instanceId: "midomail-01"
  logLevel: INFO              # TRACE | DEBUG | INFO | WARNING | ERROR | CRITICAL

routing:
  rules:
    - ruleId: "sms-to-email-default"
      priority: 100
      enabled: true
      conditions:
        sourceChannel: "gsm"
      targetChannel: "email"
      targetAdapter: "email-primary"
      deliveryPolicy: "AT_LEAST_ONCE"
    - ruleId: "email-reply-to-sms"
      priority: 100
      enabled: true
      conditions:
        sourceChannel: "email"
      targetChannel: "gsm"
      targetAdapter: "gsm-primary"
      deliveryPolicy: "AT_LEAST_ONCE"
    - ruleId: "webhook-high-priority"
      priority: 200                # wyższy priorytet reguły = ewaluowana wcześniej niż reguły powyżej
      enabled: true
      conditions:
        sourceChannel: "webhook"   # Conditions odwołuje się wyłącznie do ChannelType — jedynego pola
                                    # wykorzystywanego przez Routing Engine do decyzji (ADR-0010);
                                    # ani address, ani AdapterId nigdy nie są warunkiem (ADR-0012).
      targetChannel: "email"
      targetAdapter: "email-primary"
      deliveryPolicy: "AT_LEAST_ONCE"
      setPriority: HIGH             # nadpisuje MessagePriority komunikatu, nie mylić z priority reguły powyżej

adapters:
  - adapterId: "email-primary"
    type: "email"
    enabled: true
    config:
      address: "user@example.com"
      displayName: "midoMAIL Gateway"
      smtp:
        host: "smtp.gmail.com"
        port: 587
        security: STARTTLS     # SSL | STARTTLS
      imap:
        host: "imap.gmail.com"
        port: 993
        security: IMAPS         # IMAPS | STARTTLS
        folder: "INBOX"
        syncIntervalSeconds: 60
      credentials:
        secretRef: "email-primary/credentials"   # referencja do Secret Store, nigdy jawne hasło
      timeoutMs: 10000
      retry:
        maxAttempts: 3
        backoffMs: 2000

  - adapterId: "gsm-primary"
    type: "gsm"
    enabled: true
    config:
      simSlot: 0                # tylko gdy platforma wspiera multi-SIM (21-Adapter-GSM.md, §8)
      smscOverride: null
      deliveryReports: true
    rateLimiting:                # opcjonalne — per adapter, per operacja (SPEC-0011)
      send:
        capacity: 30
        refillPerMinute: 30
      receive:
        capacity: 60
        refillPerMinute: 60

  - adapterId: "websocket-primary"
    type: "websocket"            # Faza 7, SPEC-0026-WebSocket-Adapter-Contract.md
    enabled: true
    config:
      url: "wss://example.com/realtime"   # Gateway łączy się wychodząco (klient, nie serwer)
      reconnect:
        maxAttempts: 5
        backoffMs: 2000
      heartbeatIntervalMs: 30000

scheduler:
  tasks:
    - taskId: "email-poll"
      intervalSeconds: 60

security:
  secretStore: "android-keystore"   # android-keystore | env | file

monitoring:
  healthCheckIntervalSeconds: 30

messageStore:
  retentionDays: 30
  deduplicationRetentionDays: 365

notifications:                     # SPEC-0011 nie dotyczy; patrz 30-Infrastructure/38-Powiadomienia.md
  channels:
    - channelId: "ops-email"
      type: EMAIL                  # EMAIL | PUSH | WEBHOOK
      address: "ops@example.com"
    - channelId: "pagerduty"
      type: WEBHOOK
      url: "https://events.pagerduty.com/integration/xxxx/enqueue"
  routing:
    - level: CRITICAL
      channels: ["pagerduty", "ops-email"]
      escalateAfterMinutes: 5
    - level: ERROR
      channels: ["ops-email"]
      escalateAfterMinutes: 30
    - level: WARNING
      channels: ["ops-email"]
```

---

# Typy, wartości domyślne i zakresy

| Pole | Typ | Wymagane | Domyślna | Zakres / dozwolone wartości |
|---|---|---|---|---|
| `version` | string | tak | — | zgodna z wersją kontraktu Configuration Model |
| `gateway.instanceId` | string | tak | — | niepusty, unikalny w obrębie wdrożenia |
| `gateway.logLevel` | enum | nie | `INFO` | TRACE, DEBUG, INFO, WARNING, ERROR, CRITICAL |
| `routing.rules[].ruleId` | string | tak | — | unikalny w obrębie pliku |
| `routing.rules[].priority` | int | tak | — | ≥ 0; nie musi być unikalny (patrz §Walidacja krzyżowa) |
| `routing.rules[].enabled` | bool | nie | `true` | — |
| `routing.rules[].targetAdapter` | string | tak | — | musi odnosić się do istniejącego `adapters[].adapterId` |
| `routing.rules[].conditions.sourceChannel` | string | nie | brak warunku | odnosi się wyłącznie do `ChannelType`; nigdy `address` ani `AdapterId` (ADR-0010, ADR-0012) |
| `routing.rules[].setPriority` | enum | nie | — (brak nadpisania) | LOW, NORMAL, HIGH, CRITICAL — odrębne od `routing.rules[].priority` (patrz SPEC-0007-Routing-Contract.md, uwaga terminologiczna) |
| `adapters[].adapterId` | string | tak | — | unikalny w obrębie pliku |
| `adapters[].type` | enum | tak | — | email, gsm, rest, websocket, cli (20-Adapters) |
| `adapters[].enabled` | bool | nie | `true` | — |
| `adapters[].config.smtp.port` | int | zależne | `587` dla STARTTLS, `465` dla SSL | 1–65535 |
| `adapters[].config.imap.port` | int | zależne | `993` dla IMAPS, `143` dla STARTTLS | 1–65535 |
| `adapters[].config.credentials.secretRef` | string | zależne | — | musi wskazywać istniejący wpis w Secret Store |
| `adapters[].config.retry.maxAttempts` | int | nie | `3` | 0–10 |
| `adapters[].config.simSlot` | int | nie | `0` | ≥ 0; tylko jeśli platforma raportuje obsługę multi-SIM |
| `scheduler.tasks[].intervalSeconds` | int | tak | — | ≥ 5 (dolny limit chroniący przed nadmiernym obciążeniem transportu) |
| `security.secretStore` | enum | tak | — | android-keystore, env, file — zgodny z platformą wdrożenia |
| `messageStore.retentionDays` | int | nie | `30` | ≥ 1 |
| `messageStore.deduplicationRetentionDays` | int | nie | `365` | ≥ `messageStore.retentionDays` (patrz §Walidacja krzyżowa) |
| `adapters[].rateLimiting.<op>.capacity` | int | nie | brak limitu | ≥ 1; `<op>` to `send` lub `receive` (SPEC-0011-Rate-Limiting-Contract.md) |
| `adapters[].rateLimiting.<op>.refillPerMinute` | int | nie | brak limitu | ≥ 1 |
| `notifications.channels[].channelId` | string | tak | — | unikalny w obrębie pliku |
| `notifications.channels[].type` | enum | tak | — | EMAIL, PUSH, WEBHOOK (30-Infrastructure/38-Powiadomienia.md, §3) |
| `notifications.channels[].url` | string | zależne | — | wymagane dla `type: WEBHOOK`; poprawny URL HTTP(S) |
| `notifications.routing[].level` | enum | tak | — | INFO, WARNING, ERROR, CRITICAL |
| `notifications.routing[].channels` | list[string] | tak | — | każdy element musi odnosić się do istniejącego `notifications.channels[].channelId` |
| `notifications.routing[].escalateAfterMinutes` | int | nie | brak eskalacji | ≥ 1 |

---

# Walidacja krzyżowa

Konfiguracja jest odrzucana przed zapisaniem (60-User-Interface/65-Konfiguracja.md, §Funkcje), jeżeli którakolwiek z poniższych reguł jest naruszona:

- Jeżeli `adapters[].type == "email"` — wymagane są `config.smtp.host`, `config.smtp.port`, `config.imap.host`, `config.imap.port`, `config.credentials.secretRef`.
- Jeżeli `adapters[].type == "gsm"` — `config.simSlot` (jeśli podany) musi odpowiadać rzeczywiście dostępnemu gniazdu SIM raportowanemu przez platformę (40-Platforms/40-Android.md).
- Każde `routing.rules[].targetAdapter` musi odnosić się do istniejącego `adapters[].adapterId` — odwołanie do nieistniejącego adaptera jest błędem walidacji, nie cichym pominięciem reguły.
- `routing.rules[].priority` **nie musi być unikalny**; przy równym priorytecie reguły są ewaluowane w kolejności deklaracji w pliku (pierwsza pasująca wygrywa) — wymagane dla determinizmu routingu (10-Core/13-Routing.md, §3: „Wynik routingu jest deterministyczny").
- `routing.rules[].setPriority`, jeśli obecne, musi być jedną z wartości `MessagePriority` (LOW, NORMAL, HIGH, CRITICAL) — nie jest to to samo pole co `routing.rules[].priority` (90-ADR/ADR-0005-Message-Priority.md).
- `routing.rules[].conditions` może odwoływać się wyłącznie do `ChannelType` (np. `sourceChannel`) — jakikolwiek warunek odwołujący się do `address` lub `AdapterId` jest błędem walidacji (ADR-0010-Model-Channel.md, ADR-0012-Channel-AdapterId.md).
- `messageStore.deduplicationRetentionDays` musi być **≥** `messageStore.retentionDays` — rekord deduplikacji Exactly Once musi przeżyć dłużej niż treść komunikatu (91-Specification/SPEC-0009-Message-Store-Contract.md, §Retention policy); odwrotna relacja jest błędem walidacji.
- `adapters[].enabled == true` wymaga, aby cała sekcja `config` tego adaptera przeszła własną walidację (np. adapter Email nie może być włączony z niekompletną konfiguracją SMTP/IMAP).
- `scheduler.tasks[].taskId` musi być unikalny w obrębie pliku.
- `security.secretStore` musi być zgodny z platformą uruchomieniową (np. `android-keystore` dopuszczalny tylko gdy platforma to Android — 40-Platforms/40-Android.md; `env`/`file` dla JVM/Linux — 40-Platforms/41-JVM.md, 42-Linux.md).
- Każde `notifications.routing[].channels[]` musi odnosić się do istniejącego `notifications.channels[].channelId` — odwołanie do nieistniejącego kanału jest błędem walidacji.
- `notifications.channels[].url` jest wymagane wtedy i tylko wtedy, gdy `type == WEBHOOK`; dla `type == EMAIL` wymagane jest `address`, dla `type == PUSH` platforma musi raportować obsługę push (40-Platforms/40-Android.md).

---

# Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 30-Infrastructure/30-Konfiguracja.md
- 30-Infrastructure/31-Bezpieczenstwo.md
- 30-Infrastructure/38-Powiadomienia.md
- 60-User-Interface/65-Konfiguracja.md
- 90-ADR/ADR-0004-Format-Konfiguracji.md
- 90-ADR/ADR-0005-Message-Priority.md
- 90-ADR/ADR-0006-Rate-Limiting.md
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0002-Porty.md
- 91-Specification/SPEC-0007-Routing-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md