# midoMAIL 2.0

# Dokument 41 — Platforma JVM

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument opisuje wykorzystanie środowiska JVM jako platformy uruchomieniowej dla Communication Gateway. JVM umożliwia uruchomienie midoMAIL poza systemem Android przy zachowaniu tej samej architektury rdzenia.

---

# 2. Rola platformy

Platforma JVM dostarcza wyłącznie usługi infrastrukturalne wymagane przez adaptery i komponenty pomocnicze, takie jak:

- uruchamianie procesu Gateway,
- dostęp do systemu plików,
- komunikację sieciową,
- planowanie zadań,
- integrację z usługami systemowymi.

---

# 3. Zasady architektoniczne

- Core pozostaje niezależny od JVM.
- Kod specyficzny dla JVM znajduje się wyłącznie w warstwie platformy.
- Integracja z rdzeniem odbywa się wyłącznie przez porty.
- Platforma może być wymieniona bez wpływu na model domenowy.

---

# 4. Typowe zastosowania

- serwer lokalny,
- środowiska testowe,
- kontenery,
- systemy integracyjne,
- narzędzia administracyjne.

---

# 5. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 18-Porty
- 30-Konfiguracja
- 43-Przenosnosc
- 52-Deployment