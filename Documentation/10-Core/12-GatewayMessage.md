# midoMAIL 2.0

# Dokument 12 — GatewayMessage

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

GatewayMessage jest kanonicznym modelem komunikatu wykorzystywanym przez Communication Gateway. Każdy adapter tłumaczy własny format na GatewayMessage przed przekazaniem go do Gateway Engine oraz odwrotnie przy opuszczaniu platformy.

Model GatewayMessage jest całkowicie niezależny od transportu, platformy oraz technologii implementacji.

---

# 2. Założenia projektowe

GatewayMessage:

- nie zawiera informacji specyficznych dla SMS, E-mail ani innych transportów,
- jest jedynym modelem akceptowanym przez Gateway Engine,
- posiada stabilny kontrakt i wersjonowanie,
- umożliwia implementację Exactly Once Processing,
- może być serializowany bez utraty informacji.

---

# 3. Model logiczny

Każdy GatewayMessage składa się z następujących części (ADR-0011-Metadata-Attributes-Scalenie.md — `Metadata` i `Attributes` scalone w jedno pole):

- Identity (tożsamość komunikatu, w tym ExternalReference i MessagePriority),
- Source (kanał źródłowy),
- Destination (kanał docelowy),
- Payload (ładunek, w tym opcjonalne załączniki),
- Attributes (atrybuty rozszerzające),
- Processing Context (kontekst przetwarzania).

---

# 4. Identity

Identity jednoznacznie identyfikuje komunikat w całym cyklu życia systemu.

Minimalne wymagania:

- globalnie unikalny identyfikator (MessageId),
- identyfikator korelacyjny (CorrelationId),
- identyfikator przyczynowy (CausationId, opcjonalnie),
- wersja modelu (SchemaVersion).

---

# 5. ExternalReference (Source Event Identity)

Oprócz wewnętrznego `MessageId` model musi przechowywać **ExternalReference** (SourceEventId) dostarczany przez adapter. Jest to naturalny identyfikator komunikatu w systemie źródłowym (np. RFC 5322 `Message-ID`, identyfikator PDU SMS, `Idempotency-Key` REST).

`MessageId` identyfikuje komunikat wewnątrz Communication Gateway, natomiast `ExternalReference` służy do wykrywania duplikatów oraz zapewnienia Exactly Once przed utworzeniem nowego GatewayMessage.

---

# 6. MessagePriority

Model zawiera pole **MessagePriority**, odrębne od `Priority` reguły routingu (10-Core/13-Routing.md, §Model reguł routingu) — `Priority` rozstrzyga, która reguła routingu wygrywa przy wielu pasujących, `MessagePriority` opisuje priorytet samej wiadomości (ADR-0005-Message-Priority.md).

Dopuszczalne wartości: `LOW`, `NORMAL` (domyślna), `HIGH`, `CRITICAL`. Wartość początkowa może zostać nadana przez adapter źródłowy przy tworzeniu komunikatu; reguła routingu może ją nadpisać poprzez akcję `SetPriority` podczas wyznaczania trasy (91-Specification/SPEC-0007-Routing-Contract.md). Wartość obowiązująca po zakończeniu routingu wpływa na kolejkowanie w Schedulerze (10-Core/16-Scheduler.md) oraz jest polem indeksowanym w Message Store (91-Specification/SPEC-0009-Message-Store-Contract.md).

`MessagePriority` nie tworzy odrębnych torów przetwarzania ani gwarancji czasowych (SLA) — wpływa wyłącznie na kolejność w obrębie tego samego stanu gotowości.

---

# 7. Source i Destination

Źródło i cel komunikatu opisują kanały logiczne, a nie transport.

Gateway nie podejmuje decyzji na podstawie adresów, numerów telefonów ani adresów e-mail. Decyzje wynikają wyłącznie z jawnie zdefiniowanych metadanych modelu.

Zgodnie z ADR-0010-Model-Channel.md i ADR-0012-Channel-AdapterId.md, model współdzielony przez Source i Destination (`Channel`) składa się z:

- `ChannelType` — identyfikator logicznego typu kanału (np. `gsm`, `email`) — jedyna „jawnie zdefiniowana metadana", na podstawie której Routing Engine podejmuje decyzje.
- `address` (opcjonalny) — konkretny adres transportowy, nieinterpretowany przez Core, przenoszony wyłącznie do skonsumowania przez adapter. Nigdy nie jest warunkiem reguły routingu.
- `adapterId` (opcjonalny) — identyfikator zarejestrowanego adaptera obsługującego ten kanał; znany od razu dla Source, wypełniany decyzją routingu dla Destination. Wykorzystywany do indeksowania w Message Store (SPEC-0009-Message-Store-Contract.md).

---

# 8. Payload

Payload przechowuje właściwą treść komunikatu w postaci niezależnej od transportu. Odpowiedzialność za mapowanie danych spoczywa na adapterach.

Payload jest modelem wieloczęściowym, złożonym z:

- treści głównej (tekst, niezależny od transportu),
- listy załączników (Attachments) — opcjonalnej, pustej dla komunikatów czysto tekstowych.

Każdy załącznik opisuje: typ MIME, nazwę pliku, rozmiar oraz referencję do danych binarnych. Gateway nie interpretuje zawartości załącznika — mapowanie i (jeśli wymagane przez transport docelowy) transkodowanie leży po stronie adaptera.

Gateway nie przechowuje trwale danych binarnych załączników poza czasem przetwarzania komunikatu — nie pełni roli archiwum plików (30-Infrastructure/31-Bezpieczenstwo.md, zasada minimalizacji danych).

Adapter deklaruje obsługę załączników jako możliwość (`SupportsAttachments`, SPEC-0006-Adapter-Contract.md, §SupportedCapabilities). Routing Engine uwzględnia tę możliwość przy wyznaczaniu adaptera docelowego dla komunikatu z załącznikiem — komunikat z załącznikiem skierowany do adaptera niewspierającego załączników jest odrzucany lub degradowany zgodnie z polityką routingu, nigdy niecicho pomijany (30-Infrastructure/34-Error-Handling.md).

---

# 9. Attributes

Generyczny, rozszerzalny bagaż par klucz-wartość, dołączany przez adaptery lub Gateway Engine do celów rozszerzeń, bez z góry ustalonej struktury (ADR-0011-Metadata-Attributes-Scalenie.md; 91-Specification/SPEC-0001-GatewayMessage.md, §Attributes). Nie stanowi kanału do przekazywania danych biznesowych — do tego służy Payload.

---

# 10. Processing Context

Kontekst przetwarzania zawiera informacje niezbędne do realizacji routingu, polityki Exactly Once, monitorowania oraz diagnostyki. Nie stanowi części danych biznesowych komunikatu.

---

# 11. Ograniczenia

GatewayMessage nie może zawierać logiki biznesowej ani zależności od infrastruktury. Jest niezmiennym modelem domenowym wykorzystywanym przez cały cykl przetwarzania.

---

# 12. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 00-Foundation/03-Model-domenowy.md
- 10-Core/11-Gateway-Engine.md
- 10-Core/13-Routing.md
- 10-Core/16-Scheduler.md
- 10-Core/17-Exactly-Once.md
- 10-Core/18-Porty.md
- 20-Adapters/21-Adapter-GSM.md
- 20-Adapters/22-Adapter-Email.md
- 90-ADR/ADR-0003-Domyslna-aplikacja-SMS-MMS.md
- 90-ADR/ADR-0005-Message-Priority.md
- 90-ADR/ADR-0010-Model-Channel.md
- 90-ADR/ADR-0011-Metadata-Attributes-Scalenie.md
- 90-ADR/ADR-0012-Channel-AdapterId.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0007-Routing-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
