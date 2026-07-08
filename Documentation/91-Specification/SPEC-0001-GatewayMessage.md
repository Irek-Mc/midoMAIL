# SPEC-0001 — GatewayMessage

**Status:** Accepted
**Powiązany dokument:** 10-Core/12-GatewayMessage.md

---

# Cel

Dokument stanowi techniczną specyfikację modelu GatewayMessage. Definiuje kontrakt wykorzystywany przez wszystkie adaptery oraz rdzeń Communication Gateway.

---

# Założenia

- GatewayMessage jest jedynym modelem komunikatu akceptowanym przez Gateway Engine.
- Model jest niezależny od transportu i platformy.
- Model jest niezmienny (immutable).
- Model podlega wersjonowaniu.

---

# Struktura logiczna

GatewayMessage składa się z następujących sekcji (ADR-0011-Metadata-Attributes-Scalenie.md — `Metadata` i `Attributes` scalone w jedno pole `Attributes`):

1. Identity
2. Source
3. Destination
4. Payload
5. Attributes
6. ProcessingContext

---

# Identity

Minimalny kontrakt:

- MessageId
- CorrelationId
- CausationId
- SchemaVersion
- ExternalReference
- MessagePriority

MessageId musi być globalnie unikalny.

---

# MessagePriority

```kotlin
enum class MessagePriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}
```

- Wartość domyślna: `NORMAL` — komunikat/adapter, który jej nie ustawia, otrzymuje ten priorytet.
- Odrębne od `Priority` reguły routingu (SPEC-0007-Routing-Contract.md, §Model reguły routingu) — nie mylić tych dwóch pól.
- Adapter źródłowy może nadać wartość początkową przy tworzeniu komunikatu.
- Reguła routingu może nadpisać wartość poprzez opcjonalną akcję `SetPriority` (SPEC-0007-Routing-Contract.md).
- Wartość obowiązująca po routingu wpływa na kolejność w Schedulerze (10-Core/16-Scheduler.md) oraz jest polem indeksowanym i filtrowalnym w Message Store (SPEC-0009-Message-Store-Contract.md, §Schemat).
- Nie tworzy odrębnych torów przetwarzania ani gwarancji czasowych (SLA) — wyłącznie kolejność w obrębie tego samego stanu gotowości (ADR-0005-Message-Priority.md).

---

# Source

Opisuje logiczny kanał źródłowy.

Nie zawiera informacji zależnych od transportu.

---

# Destination

Opisuje logiczny kanał docelowy.

Routing wykorzystuje wyłącznie ten model.

---

# Channel (model współdzielony przez Source i Destination)

Zgodnie z ADR-0010-Model-Channel.md i ADR-0012-Channel-AdapterId.md, `Channel` składa się z:

- `ChannelType` — identyfikator logicznego typu kanału (np. `gsm`, `email`, `rest`, `websocket`, `cli`). Jedyne pole wykorzystywane przez Routing Engine do podejmowania decyzji.
- `address` (opcjonalny) — konkretny adres transportowy (numer telefonu, adres e-mail, URL webhooka), zależny od `ChannelType`. Nieinterpretowane przez Core — wyłącznie przenoszone do adaptera. Nigdy nie jest warunkiem reguły routingu.
- `adapterId` (opcjonalny) — identyfikator zarejestrowanego adaptera obsługującego ten kanał (ten sam `AdapterId`, którego używa Registry Adapterów i Plugin SDK). Znany od razu dla Source; dla Destination wypełniany wynikiem decyzji routingu (`TargetAdapter`, SPEC-0007-Routing-Contract.md). Wykorzystywany przez Message Store do indeksowania (SPEC-0009-Message-Store-Contract.md, §Schemat).

---

# Payload

Payload przechowuje dane biznesowe komunikatu. Format danych jest interpretowany przez adaptery oraz logikę domenową zgodnie z kontraktami.

Minimalny kontrakt Payload:

- Content: treść główna (tekst), niezależna od transportu.
- Attachments: lista (możliwie pusta) obiektów Attachment.

Minimalny kontrakt Attachment:

- ContentType (typ MIME),
- FileName,
- Size (rozmiar w bajtach),
- DataReference (referencja do danych binarnych; Gateway nie przechowuje danych trwale poza czasem przetwarzania — patrz 30-Infrastructure/31-Bezpieczenstwo.md). Rozwiązanie do rzeczywistych bajtów: port `AttachmentStore`, ADR-0013-Attachment-Store.md, SPEC-0016-Attachment-Store-Contract.md.

Adapter docelowy deklaruje `SupportsAttachments` w SupportedCapabilities (SPEC-0006-Adapter-Contract.md). Routing Engine odrzuca lub degraduje (zgodnie z polityką) komunikat z niepustą listą Attachments skierowany do adaptera bez tej możliwości — nigdy nie pomija załącznika bez jawnej decyzji routingu.

---

# Attributes

Generyczny, rozszerzalny bagaż par klucz-wartość (`Map<String, String>`, domyślnie pusty), dołączany przez adaptery lub Gateway Engine do celów rozszerzeń, bez z góry ustalonej struktury (ADR-0011-Metadata-Attributes-Scalenie.md). Nie stanowi kanału do przekazywania danych biznesowych — do tego służy Payload.

---

# ProcessingContext

Sekcja wykorzystywana przez Gateway podczas przetwarzania. Nie stanowi części danych biznesowych przekazywanych pomiędzy systemami.

---

# Zasady zgodności

Każda zmiana kontraktu GatewayMessage wymaga:

- aktualizacji dokumentacji Core,
- utworzenia lub aktualizacji ADR,
- zachowania zgodności wersji kontraktu.

## ExternalReference
ExternalReference (SourceEventId) jest obowiązkowym elementem kontraktu. Adapter przekazuje naturalny identyfikator komunikatu źródłowego. Mechanizm Exactly Once wykorzystuje ExternalReference do wykrywania duplikatów przed utworzeniem nowego GatewayMessage.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 10-Core/16-Scheduler.md
- 90-ADR/ADR-0005-Message-Priority.md
- 90-ADR/ADR-0010-Model-Channel.md
- 90-ADR/ADR-0011-Metadata-Attributes-Scalenie.md
- 90-ADR/ADR-0012-Channel-AdapterId.md
- 90-ADR/ADR-0013-Attachment-Store.md
- SPEC-0004-Processing-State.md
- SPEC-0007-Routing-Contract.md
- SPEC-0008-Exactly-Once-Contract.md
- SPEC-0009-Message-Store-Contract.md
- SPEC-0016-Attachment-Store-Contract.md
