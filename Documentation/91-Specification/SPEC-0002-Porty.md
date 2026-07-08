# SPEC-0002 — Porty

**Status:** Accepted
**Powiązany dokument:** 10-Core/18-Porty.md

---

# Cel

Dokument definiuje techniczne kontrakty portów wykorzystywanych przez Communication Gateway. Port stanowi stabilny interfejs pomiędzy Core a adapterami oraz infrastrukturą.

---

# Kategorie portów

- Input Ports
- Output Ports
- Infrastructure Ports
- Administration Ports
- Plugin Extension Ports

---

# Wymagania kontraktów

Każdy port:

- posiada jednoznaczną odpowiedzialność,
- nie ujawnia szczegółów implementacyjnych,
- jest wersjonowany,
- jest niezależny od platformy,
- umożliwia implementacje testowe.

---

# Zasady projektowe

- Port definiuje wyłącznie kontrakt.
- Implementacje znajdują się poza Core.
- Zmiana kontraktu wymaga aktualizacji dokumentacji architektonicznej oraz odpowiedniego ADR.

---

# Plan dalszej specyfikacji

Dla każdego portu zostanie przygotowana osobna specyfikacja obejmująca:

- sygnatury operacji,
- model danych wejściowych i wyjściowych,
- semantykę błędów,
- wymagania Exactly Once,
- wymagania wydajnościowe,
- zgodność wersji.

Dotychczas przygotowane: Message Store (SPEC-0009), Rate Limiter (SPEC-0011), Configuration Provider (SPEC-0012), Scheduler Provider (SPEC-0013).

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/18-Porty.md
- SPEC-0001-GatewayMessage.md
- SPEC-0006-Adapter-Contract.md
- SPEC-0009-Message-Store-Contract.md
- SPEC-0011-Rate-Limiting-Contract.md
- SPEC-0012-Configuration-Provider-Contract.md
- SPEC-0013-Scheduler-Provider-Contract.md