# SPEC-0006 — Adapter Contract

**Status:** Accepted
**Powiązane dokumenty:** 19-Plugin-SDK.md, 20-Transporty.md

---

# Cel

Dokument definiuje kanoniczny kontrakt implementacyjny dla wszystkich adapterów współpracujących z Communication Gateway.

---

# Założenia

- Adapter jest elementem infrastruktury.
- Adapter nie zawiera logiki biznesowej.
- Adapter komunikuje się z Core wyłącznie przez Porty.
- Adapter mapuje dane transportu do modelu GatewayMessage i odwrotnie.

---

# Odpowiedzialność adaptera

Adapter odpowiada za:

- odbiór komunikatów z transportu,
- walidację techniczną danych wejściowych,
- mapowanie do GatewayMessage,
- wysyłanie komunikatów do transportu,
- raportowanie stanu i błędów.

---

# Minimalny kontrakt

Każdy adapter powinien udostępniać:

- AdapterId,
- AdapterVersion,
- SupportedChannels,
- SupportedCapabilities,
- HealthStatus,
- Metrics (w tym stan Rate Limitera — dostępne żetony, liczba throttled, skumulowana liczba zdarzeń throttlingu; SPEC-0011-Rate-Limiting-Contract.md, §Obserwowalność).

Pola `HealthStatus` i `Metrics`: 91-Specification/SPEC-0015-Adapter-Observability-Contract.md.

---

# Przykładowe SupportedCapabilities

- `SupportsAttachments` — adapter potrafi przyjąć/wysłać Payload z niepustą listą Attachments (SPEC-0001-GatewayMessage.md, §Payload).
- `SupportsMms` — adapter GSM obsługuje MMS jako odrębny transport (ADR-0003, 20-Adapters/21-Adapter-GSM.md).
- `SupportsMultipart` — adapter dzieli długą treść tekstową na wiele segmentów transportu (np. Multipart SMS).

Routing Engine odczytuje te możliwości przy wyznaczaniu adaptera docelowego dla komunikatu — komunikat z załącznikiem nie jest kierowany do adaptera bez `SupportsAttachments` (SPEC-0007-Routing-Contract.md).

---

# Cykl życia

Nazewnictwo stanów zgodne z 10-Core/14-Registry-Adapterow.md, §4 (pełna tabela przejść:
SPEC-0014-Adapter-Lifecycle-Contract.md):

1. Registered
2. Initializing
3. Ready
4. Busy
5. Degraded (opcjonalnie)
6. Stopping
7. Stopped
8. Failed

---

# Wymagania jakościowe

- zgodność z Plugin SDK,
- pełna obserwowalność,
- testowalność poprzez implementacje zastępcze,
- zgodność z polityką bezpieczeństwa,
- współpraca z mechanizmami Exactly Once w zakresie określonym przez kontrakty.

---

# ExternalReference

ExternalReference (SourceEventId) jest obowiązkowym elementem kontraktu. Adapter przekazuje naturalny identyfikator komunikatu źródłowego. Mechanizm Exactly Once wykorzystuje ExternalReference do wykrywania duplikatów przed utworzeniem nowego GatewayMessage.

---

# Implementowalny kontrakt

Konkretny interfejs (`Adapter`, `Capability`, `AdapterFactory`, `AdapterPorts`) oraz przepływ rejestracji, konfiguracji i wstrzykiwania zależności są zdefiniowane w SPEC-0010-Plugin-SDK-Contract.md — niniejszy dokument opisuje wymagany zakres kontraktu, SPEC-0010 opisuje jego dokładny kształt w kodzie.

---

# Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 10-Core/14-Registry-Adapterow.md
- 19-Plugin-SDK.md
- 20-Transporty.md
- 90-ADR/ADR-0006-Rate-Limiting.md
- SPEC-0001-GatewayMessage.md
- SPEC-0002-Porty.md
- SPEC-0010-Plugin-SDK-Contract.md
- SPEC-0011-Rate-Limiting-Contract.md
- SPEC-0014-Adapter-Lifecycle-Contract.md
- SPEC-0015-Adapter-Observability-Contract.md
