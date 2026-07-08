# midoMAIL 2.0

# Dokument 61 — Dashboard Gateway

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Dashboard jest głównym ekranem operacyjnym Communication Gateway. Ma umożliwiać ocenę stanu systemu w czasie krótszym niż 10 sekund.

---

# 2. Sekcje ekranu

### Status Gateway
- Running / Degraded / Stopped
- Wersja
- Czas pracy

### Adaptery
- Nazwa
- Stan
- Ostatnia aktywność
- Błędy

### Kolejki
Model kolejek prezentuje stan zadań Schedulera, a nie Processing State komunikatu. W obrębie stanu Waiting kolejka jest uszeregowana według MessagePriority (10-Core/16-Scheduler.md, §5) — Dashboard pokazuje liczebność w podziale na LOW/NORMAL/HIGH/CRITICAL.

- Waiting
- Processing
- Retrying
- Failed

### Exactly Once
- Processed
- Duplicates prevented
- Recovered
- Failed

### Monitoring
- CPU
- RAM
- Storage
- Network

### Zdarzenia
- Ostatnie błędy
- Ostrzeżenia
- Informacje

---

# 3. Operacje

- Restart Gateway
- Reload Configuration
- Diagnostics
- Open Logs
- Manage Adapters

---

# 4. Wymagania dla Core

Każda wartość prezentowana na Dashboard musi być udostępniona przez jawny kontrakt architektoniczny. Dashboard nie wykonuje obliczeń biznesowych.

---

# 5. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/11-Gateway-Engine.md
- 10-Core/16-Scheduler.md
- 30-Infrastructure/35-Health-Monitor.md
- 90-ADR/ADR-0005-Message-Priority.md
