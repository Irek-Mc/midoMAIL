# midoMAIL 2.0

# Dokument 60 — Filozofia UX

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Interfejs użytkownika jest częścią architektury Communication Gateway, a nie warstwą kosmetyczną. Każdy ekran odzwierciedla możliwości architektury i nie zawiera logiki biznesowej.

---

# 2. Zasady

- Dashboard pokazuje rzeczywisty stan Gateway.
- Wszystkie operacje administracyjne są wykonywane poprzez kontrakty Core.
- UI nie komunikuje się bezpośrednio z adapterami transportowymi (GSM, Email, WebSocket) ani z Gateway Engine — wyłącznie z Adapterem REST lub Adapterem CLI (patrz §4, ADR-0002).
- Wszystkie akcje są audytowalne.
- Każda informacja prezentowana w UI musi pochodzić z jawnie zdefiniowanego kontraktu architektonicznego.

---

# 3. Główne obszary UI

- Dashboard
- Komunikaty
- Routing
- Adaptery
- Konfiguracja
- Monitoring
- Diagnostyka
- Statystyki
- Logi
- Użytkownicy i uprawnienia

---

# 4. Integracja z architekturą

UI nie jest adapterem i nie jest rejestrowane w Registry Adapterów. UI jest klientem Adaptera REST lub Adaptera CLI. Wszystkie operacje przechodzą przez publiczne kontrakty tych adapterów; Core nie jest wywoływany bezpośrednio przez UI (ADR-0002).

---

# 5. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 20-Adapters/23-Adapter-REST.md
- 20-Adapters/25-Adapter-CLI.md
- 10-Core/18-Porty.md
- 90-ADR/ADR-0002-UI-jako-klient-adaptera.md
