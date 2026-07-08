# midoMAIL 2.0

# Dokument 17 — Exactly Once

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje wymagania architektoniczne oraz odpowiedzialność mechanizmu Exactly Once Processing zapewniającego, że komunikat zostanie przetworzony dokładnie jeden raz z punktu widzenia domeny biznesowej.

---

# 2. Odpowiedzialność

Mechanizm Exactly Once odpowiada za:

- identyfikację komunikatów,
- weryfikację ExternalReference przed utworzeniem nowego GatewayMessage,
- wykrywanie duplikatów,
- kontrolę stanu przetwarzania,
- bezpieczne wznawianie operacji po awarii,
- współpracę z Gateway Engine i Schedulerem.

Mechanizm nie odpowiada za routing, komunikację z adapterami ani logikę biznesową.

---

# 3. Założenia architektoniczne

- każdy komunikat posiada globalnie unikalny MessageId oraz ExternalReference pochodzący z systemu źródłowego,
- stan przetwarzania jest trwały,
- decyzje o ponownym wykonaniu są deterministyczne,
- komponent działa niezależnie od transportu,
- polityki deduplikacji są konfigurowalne.

---

# 4. Cykl przetwarzania

1. Rejestracja komunikatu.
2. Weryfikacja wcześniejszego przetworzenia na podstawie ExternalReference.
3. Rezerwacja przetwarzania.
4. Wykonanie operacji biznesowej.
5. Zatwierdzenie wyniku.
6. Publikacja zdarzeń końcowych.

---

# 5. Integracja

Exactly Once współpracuje z:

- Gateway Engine,
- GatewayMessage,
- Scheduler,
- Event Bus,
- Message Store (poprzez port),
- Health Monitor.

---

# 6. Ograniczenia

Mechanizm Exactly Once nie gwarantuje niezawodności transportu. Odpowiada wyłącznie za spójność przetwarzania w granicach architektury Communication Gateway.

---

# 7. Ponowne przetworzenie

Ponowne przetworzenie jest świadomą operacją administracyjną (patrz 60-User-Interface/62-Komunikaty.md, SPEC-0008-Exactly-Once-Contract.md §Ręczne ponowne przetworzenie). Tworzony jest nowy proces z nowym MessageId, natomiast ExternalReference pozostaje powiązane z historią i jest oznaczane jako administracyjne wznowienie zgodnie z polityką Exactly Once.

---

# 8. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 11-Gateway-Engine
- 12-GatewayMessage
- 15-Event-Bus
- 16-Scheduler
- 18-Porty
- 91-Specification/SPEC-0008-Exactly-Once-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
