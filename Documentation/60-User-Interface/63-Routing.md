# midoMAIL 2.0

# Dokument 63 — Routing

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran umożliwia konfigurację, analizę oraz testowanie reguł routingu Communication Gateway bez ingerencji w kod.

---

# 2. Widok reguł

Każda reguła prezentuje pola modelu reguły routingu (10-Core/13-Routing.md, §Model reguł routingu):

- RuleId (Identyfikator)
- Priority (Priorytet reguły — kolejność ewaluacji, nie mylić z MessagePriority poniżej)
- Enabled (Status)
- Conditions (Warunki wejściowe)
- Kanał źródłowy
- TargetChannel (Kanał docelowy)
- TargetAdapter (Adapter docelowy)
- DeliveryPolicy (Strategia dostarczenia)
- SetPriority (opcjonalne nadpisanie MessagePriority komunikatu — 91-Specification/SPEC-0001-GatewayMessage.md, §MessagePriority)
- Version

---

# 3. Operacje

- Dodaj regułę
- Edytuj regułę
- Wyłącz/Włącz
- Zmień priorytet reguły
- Ustaw/usuń nadpisanie MessagePriority (SetPriority)
- Testuj regułę na przykładowym GatewayMessage
- Historia zmian

---

# 4. Symulator routingu

Administrator może wprowadzić przykładowy GatewayMessage i sprawdzić:

- wybraną regułę,
- decyzję routingu,
- adapter docelowy,
- MessagePriority przed i po ewaluacji reguły (jeśli reguła zawiera SetPriority),
- uzasadnienie decyzji,
- wynik walidacji.

---

# 5. Audyt

Każda zmiana reguł jest wersjonowana i audytowana.

---

# 6. Wymagania dla Core

Core udostępnia wersjonowany model reguł routingu, mechanizm walidacji oraz możliwość wykonania symulacji bez wysyłania rzeczywistego komunikatu.

---

# 7. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/13-Routing.md
- 90-ADR/ADR-0005-Message-Priority.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0007-Routing-Contract.md
