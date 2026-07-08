# midoMAIL 2.0

# Dokument 42 — Platforma Linux

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument opisuje wykorzystanie systemu Linux jako platformy uruchomieniowej dla Communication Gateway oraz określa zasady integracji z usługami systemowymi bez naruszania architektury rdzenia.

---

# 2. Rola platformy

Platforma Linux odpowiada za dostarczenie usług infrastrukturalnych wykorzystywanych przez adaptery i komponenty pomocnicze, w szczególności:

- uruchamianie procesu Gateway,
- zarządzanie usługami systemowymi,
- komunikację sieciową,
- dostęp do systemu plików,
- integrację z mechanizmami monitorowania i automatyzacji.

---

# 3. Zasady architektoniczne

- Core pozostaje całkowicie niezależny od Linux.
- Kod specyficzny dla systemu operacyjnego znajduje się wyłącznie w warstwie platformy.
- Komunikacja z rdzeniem odbywa się wyłącznie poprzez porty.
- Wymiana platformy nie może wymagać zmian w modelu domenowym ani Gateway Engine.

---

# 4. Typowe zastosowania

- serwery fizyczne,
- maszyny wirtualne,
- kontenery,
- urządzenia edge,
- środowiska CI/CD,
- laboratoria testowe.

---

# 5. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 18-Porty
- 30-Konfiguracja
- 41-JVM
- 43-Przenosnosc
- 52-Deployment