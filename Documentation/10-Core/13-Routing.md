# midoMAIL 2.0

# Dokument 13 — Routing

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę oraz odpowiedzialność mechanizmu Routing odpowiedzialnego za wyznaczanie drogi przetwarzania komunikatów wewnątrz Communication Gateway.

---

# 2. Rola Routing Engine

Routing Engine odpowiada wyłącznie za podjęcie decyzji, gdzie i w jaki sposób komunikat powinien zostać przekazany. Nie realizuje transmisji komunikatu i nie zawiera logiki adapterów.

---

# 3. Założenia architektoniczne

- Routing jest całkowicie niezależny od transportów.
- Routing operuje wyłącznie na modelu GatewayMessage.
- Reguły routingu są konfigurowalne.
- Możliwe jest istnienie wielu polityk routingu.
- Wynik routingu jest deterministyczny dla tych samych danych wejściowych.

---

# 4. Dane wejściowe

Mechanizm wykorzystuje:

- GatewayMessage,
- konfigurację tras,
- dostępność adapterów,
- polityki routingu,
- kontekst przetwarzania.

---

# 5. Dane wyjściowe

Wynikiem działania jest decyzja routingu zawierająca:

- adapter docelowy,
- kanał docelowy,
- politykę dostarczenia,
- informacje diagnostyczne uzasadniające wybór.

---

# 6. Ograniczenia

Routing Engine:

- nie zna implementacji adapterów,
- nie komunikuje się z transportami,
- nie zapisuje danych,
- nie wykonuje operacji sieciowych,
- nie modyfikuje treści komunikatu.

---

# 7. Rozszerzalność

Nowe strategie routingu powinny być dodawane jako implementacje kontraktów bez konieczności modyfikowania Gateway Engine.

---

# 8. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 10-Architektura-systemu
- 11-Gateway-Engine
- 12-GatewayMessage
- 14-Registry-Adapterow
- 16-Scheduler
- 18-Porty
- 90-ADR/ADR-0005-Message-Priority.md
- 91-Specification/SPEC-0007-Routing-Contract.md


# Model reguł routingu
Reguła posiada: RuleId, Priority, Enabled, Conditions, TargetChannel, TargetAdapter, DeliveryPolicy, opcjonalnie SetPriority, oraz Version. Zmiany reguł są wersjonowane i audytowane.

`Priority` reguły (kolejność ewaluacji reguł) i `MessagePriority` komunikatu (91-Specification/SPEC-0001-GatewayMessage.md, §MessagePriority) to odrębne pojęcia — patrz 90-ADR/ADR-0005-Message-Priority.md. Reguła może opcjonalnie nadpisać `MessagePriority` przetwarzanego komunikatu poprzez akcję `SetPriority`.
