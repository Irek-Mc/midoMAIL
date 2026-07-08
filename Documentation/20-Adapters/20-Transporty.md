# midoMAIL 2.0

# Dokument 20 — Transporty

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje model transportów wykorzystywanych przez platformę midoMAIL 2.0 oraz zasady ich integracji z Communication Gateway.

---

# 2. Definicja transportu

Transport jest mechanizmem fizycznego przesyłania komunikatów pomiędzy systemami. Transport nie stanowi elementu logiki biznesowej Gateway i jest obsługiwany wyłącznie przez adapter.

---

# 3. Założenia architektoniczne

- Gateway jest całkowicie niezależny od transportów.
- Każdy transport jest obsługiwany przez dedykowany adapter.
- Wszystkie transporty mapują dane do modelu GatewayMessage.
- Dodanie nowego transportu nie wymaga zmian w Gateway Engine.

---

# 4. Kategorie transportów

Platforma przewiduje między innymi:

- GSM,
- SMTP/IMAP,
- REST,
- WebSocket,
- CLI,
- przyszłe transporty implementowane poprzez Plugin SDK.

---

# 5. Wymagania

Każdy transport powinien:

- zapewniać mapowanie do modelu domenowego,
- udostępniać informacje diagnostyczne,
- współpracować z mechanizmami monitorowania,
- respektować polityki bezpieczeństwa i konfiguracji,
- współpracować z mechanizmem Exactly Once w zakresie wymaganym przez architekturę.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 12-GatewayMessage
- 14-Registry-Adapterow
- 18-Porty
- 19-Plugin-SDK
