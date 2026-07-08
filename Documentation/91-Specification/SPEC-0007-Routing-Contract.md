# SPEC-0007 — Routing Contract

**Status:** Accepted
**Powiązany dokument:** 10-Core/13-Routing.md

---

# Cel

Dokument definiuje kontrakt techniczny mechanizmu Routing wykorzystywanego przez Communication Gateway do wyznaczania ścieżki przetwarzania komunikatów.

---

# Założenia

- Routing jest deterministyczny.
- Routing nie zna implementacji adapterów.
- Routing operuje wyłącznie na modelu GatewayMessage.
- Wynik routingu jest niezależny od platformy uruchomieniowej.

---

# Dane wejściowe

- GatewayMessage (w tym MessagePriority — SPEC-0001-GatewayMessage.md, §MessagePriority),
- Processing Context,
- konfiguracja routingu,
- dostępność adapterów,
- polityki biznesowe.

---

# Dane wyjściowe

Decyzja routingu zawiera:

- RouteId,
- TargetChannel,
- TargetAdapter,
- DeliveryPolicy,
- DiagnosticMetadata.

---

# Model reguły routingu

Zgodnie z 10-Core/13-Routing.md, reguła routingu (Route) posiada:

- RuleId,
- Priority,
- Enabled,
- Conditions,
- TargetChannel,
- TargetAdapter,
- DeliveryPolicy,
- SetPriority (opcjonalnie),
- Version.

Reguły są ewaluowane w kolejności malejącego Priority; pierwsza pasująca reguła o statusie Enabled=true wyznacza decyzję routingu. Zmiany reguł są wersjonowane (Version) i audytowane.

**Uwaga terminologiczna:** `Priority` reguły (powyżej) i `MessagePriority` komunikatu (SPEC-0001-GatewayMessage.md, §MessagePriority; ADR-0005-Message-Priority.md) to dwa odrębne pojęcia. `Priority` rozstrzyga, która reguła wygrywa przy wielu pasujących. `SetPriority` to opcjonalna akcja reguły, która — jeśli obecna — nadpisuje `MessagePriority` przetwarzanego komunikatu wartością zadeklarowaną w regule (np. reguła dla nadawcy oznaczonego jako VIP może ustawić `SetPriority: HIGH`).

---

# Wymagania

- możliwość rozszerzania o nowe strategie,
- pełna obserwowalność decyzji,
- zgodność z Exactly Once,
- możliwość testowania w izolacji,
- wersjonowanie kontraktu.

---

# Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 10-Core/13-Routing.md
- 10-Core/16-Scheduler.md
- 90-ADR/ADR-0005-Message-Priority.md
- SPEC-0001-GatewayMessage.md
- SPEC-0002-Porty.md
- SPEC-0004-Processing-State.md
- SPEC-0005-Configuration-Model.md