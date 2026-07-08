# midoMAIL 2.0

# Dokument 65 — Konfiguracja Gateway

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran umożliwia bezpieczne zarządzanie konfiguracją Communication Gateway bez edycji plików konfiguracyjnych.

---

# 2. Obszary konfiguracji

- Gateway
- Routing
- Adaptery
- Harmonogram
- Bezpieczeństwo
- Monitoring
- Powiadomienia (dostarczanie Alertów do kanałów zewnętrznych — kanały Email/Push/Webhook, routing wg poziomu, eskalacja: 30-Infrastructure/38-Powiadomienia.md; schemat konfiguracji: 91-Specification/SPEC-0005-Configuration-Model.md, §notifications)

---

# 3. Funkcje

Walidacja (w tym walidacja krzyżowa pomiędzy sekcjami — np. adapter Email wymaga kompletnej konfiguracji SMTP/IMAP, retencja deduplikacji musi być ≥ retencja treści) oraz format Import/Eksport (YAML) odpowiadają dokładnie kontraktowi z 91-Specification/SPEC-0005-Configuration-Model.md, §Walidacja krzyżowa i §Format pliku.

- Walidacja przed zapisaniem
- Test konfiguracji
- Podgląd zmian
- Import / Eksport (YAML)
- Historia zmian
- Przywrócenie poprzedniej wersji

---

# 4. Zarządzanie sekretami

- Hasła i klucze nigdy nie są wyświetlane w jawnej postaci.
- UI korzysta z mechanizmu bezpiecznego magazynu sekretów udostępnionego przez platformę.

---

# 5. Wymagania dla Core

Core udostępnia wersjonowaną konfigurację, walidację, historię zmian oraz możliwość bezpiecznego przeładowania bez restartu, jeśli wspiera to dany komponent.

---

# 6. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 30-Infrastructure/30-Konfiguracja.md
- 30-Infrastructure/35-Health-Monitor.md
- 30-Infrastructure/38-Powiadomienia.md
- 90-ADR/ADR-0004-Format-Konfiguracji.md
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
- 91-Specification/SPEC-0005-Configuration-Model.md
