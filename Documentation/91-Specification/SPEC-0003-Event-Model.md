# SPEC-0003 — Event Model

**Status:** Accepted
**Powiązany dokument:** 10-Core/15-Event-Bus.md

---

# Cel

Dokument definiuje techniczny model zdarzeń wykorzystywany przez Communication Gateway do komunikacji pomiędzy komponentami Core oraz warstwą infrastrukturalną.

---

# Założenia

- Zdarzenia są niemutowalne.
- Zdarzenia opisują fakty, które już wystąpiły.
- Publikacja zdarzeń jest niezależna od liczby subskrybentów.
- Każde zdarzenie posiada wersję kontraktu.

---

# Minimalny kontrakt zdarzenia

Każde zdarzenie powinno zawierać:

- EventId,
- EventType,
- EventVersion,
- Timestamp,
- CorrelationId,
- CausationId,
- SourceComponent,
- Payload.

---

# Kategorie zdarzeń

- Domain Events,
- Processing Events,
- Adapter Events,
- Infrastructure Events,
- Diagnostic Events,
- Administrative Events.

---

# Zasady zgodności

Zmiana kontraktu zdarzenia wymaga:

- aktualizacji dokumentacji Core,
- aktualizacji odpowiedniego ADR,
- zachowania zgodności wersji lub publikacji nowej wersji kontraktu.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/15-Event-Bus.md
- SPEC-0004-Processing-State.md
