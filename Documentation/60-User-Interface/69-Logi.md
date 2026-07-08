# midoMAIL 2.0

# Dokument 69 — Logi

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran zapewnia dostęp do logów operacyjnych Communication Gateway w sposób ułatwiający analizę zdarzeń bez konieczności przeglądania plików tekstowych.

---

# 2. Widok logów

Każdy wpis zawiera:

- Czas
- Poziom (Trace, Debug, Info, Warning, Error, Critical)
- Komponent
- CorrelationId
- MessageId (jeżeli dotyczy)
- ExternalReference (jeżeli dotyczy)
- Treść

---

# 3. Filtrowanie

- Zakres czasu
- Poziom
- Komponent
- Adapter
- CorrelationId
- MessageId
- ExternalReference

---

# 4. Operacje

- Strumień na żywo
- Eksport
- Kopiowanie CorrelationId
- Przejście do Message Trace
- Przejście do Diagnostyki

---

# 5. Wymagania dla Core

Core publikuje ustrukturyzowane logi z identyfikatorami korelacyjnymi. UI prezentuje logi i umożliwia ich filtrowanie, nie interpretując ich semantyki.

---

# 6. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 30-Infrastructure/33-Logowanie.md
- 30-Infrastructure/34-Error-Handling.md
