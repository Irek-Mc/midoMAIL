# midoMAIL 2.0

# Dokument 06 — Glossary

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument zawiera słownik podstawowych pojęć wykorzystywanych w dokumentacji midoMAIL 2.0. Wszystkie dokumenty projektu powinny posługiwać się terminologią zdefiniowaną w niniejszym słowniku.

---

# 2. Pojęcia podstawowe

**Communication Gateway** — rdzeń platformy odpowiedzialny za przyjmowanie, przetwarzanie, routing oraz przekazywanie komunikatów pomiędzy kanałami komunikacji.

**Communication Gateway Engine** — komponent realizujący logikę biznesową platformy oraz koordynujący przetwarzanie komunikatów.

**Message** — kanoniczny model komunikatu przetwarzanego przez Communication Gateway, niezależny od transportu.

**MessageId** — globalnie unikalny identyfikator komunikatu nadawany przez Communication Gateway przy jego przyjęciu.

**ExternalReference** (SourceEventId) — naturalny identyfikator komunikatu w systemie źródłowym, dostarczany przez adapter i wykorzystywany do wykrywania duplikatów w ramach Exactly Once Processing (np. RFC 5322 `Message-ID`, identyfikator PDU SMS, `Idempotency-Key` REST). Odróżniony od MessageId — MessageId identyfikuje komunikat wewnątrz Gateway, ExternalReference identyfikuje zdarzenie w systemie zewnętrznym.

**CorrelationId** — identyfikator wiążący ze sobą wszystkie komunikaty i zdarzenia należące do jednego procesu biznesowego (np. SMS i odpowiadający mu e-mail).

**Channel** — logiczny kanał komunikacji reprezentujący źródło lub cel komunikatu.

**Transport** — technologia lub protokół wykorzystywany do fizycznego przesłania komunikatu (np. GSM, SMTP, REST, WebSocket).

**Adapter** — komponent odpowiedzialny za integrację konkretnego transportu z Communication Gateway oraz mapowanie danych do modelu domenowego.

**Endpoint** — punkt wejścia lub wyjścia komunikacji obsługiwany przez adapter.

**Route** — reguła określająca sposób dostarczenia komunikatu pomiędzy kanałami.

**Processing Context** — komplet informacji wymaganych do przetwarzania komunikatu przez Communication Gateway.

**Processing State** — aktualny stan komunikatu w cyklu jego przetwarzania.

**Exactly Once Processing** — właściwość architektury gwarantująca, że komunikat zostanie skutecznie przetworzony dokładnie jeden raz pomimo wystąpienia błędów lub ponowień.

**Event Bus** — mechanizm komunikacji pomiędzy komponentami platformy oparty na publikacji i subskrypcji zdarzeń.

**Plugin SDK** — zestaw kontraktów umożliwiających tworzenie nowych adapterów i rozszerzeń bez modyfikacji Communication Gateway Engine.

**Health** — ustandaryzowany model kondycji komponentu (Gateway Engine, adapter, Scheduler itd.) publikowany przez Health Monitor.

**Alert** — zdarzenie operacyjne generowane przez Health Monitor lub Error Handling na podstawie zmiany stanu Health, o poziomie Info/Warning/Error/Critical, wymagające potwierdzenia lub reakcji administratora.

**Powiadomienie** — dostarczenie treści Alertu do zewnętrznego kanału (Email, Push, Webhook) poza interfejsem administracyjnym Gateway. Health Monitor wyłącznie generuje Alert; dostarczanie, routing wg poziomu i eskalacja są odpowiedzialnością odrębnego komponentu (30-Infrastructure/38-Powiadomienia.md).

**Rate Limiter** — port ograniczający przepustowość operacji adaptera (per adapter, per operacja send/receive), zapobiegający przekroczeniu limitów narzuconych przez systemy zewnętrzne (30-Konfiguracja; 91-Specification/SPEC-0011-Rate-Limiting-Contract.md).

---

# 3. Zasady utrzymania słownika

Każde nowe pojęcie wprowadzane do dokumentacji powinno zostać zdefiniowane w niniejszym dokumencie przed jego użyciem w pozostałych dokumentach projektu. Niniejszy dokument jest jedynym miejscem definicji pojęć (02-Zalozenia-architektoniczne, §Jedno źródło prawdy) — nie tworzy się odrębnych słowników w innych katalogach.

---

# 4. Dokumenty powiązane

- 00-Foundation/02-Zalozenia-architektoniczne.md
- 00-Foundation/03-Model-domenowy.md
- 10-Core/12-GatewayMessage.md
- 10-Core/17-Exactly-Once.md
- 30-Infrastructure/38-Powiadomienia.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md