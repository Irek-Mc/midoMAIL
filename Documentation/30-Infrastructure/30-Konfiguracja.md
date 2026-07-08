# midoMAIL 2.0

# Dokument 30 — Konfiguracja

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę systemu konfiguracji platformy midoMAIL 2.0. Konfiguracja umożliwia przygotowanie, uruchomienie i utrzymanie Communication Gateway bez wpływu na logikę biznesową.

---

# 2. Założenia

System konfiguracji odpowiada wyłącznie za dostarczanie parametrów do komponentów. Konfiguracja nie zawiera logiki biznesowej i nie podejmuje decyzji dotyczących przetwarzania komunikatów.

---

# 3. Zakres

Konfiguracja obejmuje:

- ustawienia Gateway,
- konfigurację adapterów,
- polityki routingu,
- harmonogramy,
- bezpieczeństwo,
- monitorowanie,
- parametry platformy.

---

# 4. Wymagania

- wersjonowanie konfiguracji,
- walidacja przed użyciem,
- możliwość importu i eksportu,
- bezpieczne przechowywanie danych wrażliwych,
- możliwość dynamicznego przeładowania wybranych ustawień.

---


# 5. Zarządzanie sekretami

Sekrety (hasła, tokeny, klucze) nie mogą być przechowywane w jawnej konfiguracji. Platformowe implementacje powinny wykorzystywać bezpieczne magazyny sekretów (np. Android Keystore) lub równoważne mechanizmy.

Port `SecretStore` (odczyt/zapis sekretu przez Core) — pełny kontrakt: 91-Specification/SPEC-0017-Secret-Store-Contract.md.

---

# 6. Format i schemat

Kanoniczny format pliku (YAML), przykładowa konfiguracja, typy/wartości domyślne/zakresy pól oraz reguły walidacji krzyżowej są zdefiniowane w 91-Specification/SPEC-0005-Configuration-Model.md (ADR-0004-Format-Konfiguracji.md dla uzasadnienia wyboru formatu).

Port `ConfigurationProvider` (odczyt konfiguracji przez Core) — minimalna sygnatura dla Fazy 1: 91-Specification/SPEC-0012-Configuration-Provider-Contract.md.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 18-Porty
- 31-Bezpieczenstwo
- 32-Baza-danych
- 35-Health-Monitor
- 90-ADR/ADR-0004-Format-Konfiguracji.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0012-Configuration-Provider-Contract.md
- 91-Specification/SPEC-0017-Secret-Store-Contract.md
