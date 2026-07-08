# midoMAIL 2.0

# Dokument 70 — Użytkownicy i uprawnienia

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran umożliwia zarządzanie dostępem do Communication Gateway zgodnie z zasadą najmniejszych uprawnień.

Dotyczy wyłącznie operatorów/administratorów Gateway. Korespondenci zewnętrzni (nadawcy/odbiorcy SMS, e-maili itd.) nie są użytkownikami w tym znaczeniu i nie mają odrębnej reprezentacji domenowej (00-Foundation/03-Model-domenowy.md, §7).

---

# 2. Role

- Administrator
- Operator
- Audytor
- Integrator
- Role niestandardowe

---

# 3. Uprawnienia

Mogą być nadawane do:

- Dashboard
- Komunikatów
- Routingu
- Adapterów
- Konfiguracji
- Monitoringu
- Diagnostyki
- Logów
- Statystyk

---

# 4. Funkcje

- Tworzenie i blokowanie kont
- Przypisywanie ról
- Reset poświadczeń
- Historia logowania
- Audyt zmian uprawnień

---

# 5. Profil wdrożenia

Model wieloużytkownikowy jest opcjonalną funkcją wdrożeniową. Na urządzeniach jednoużytkownikowych (np. Android) może zostać zastąpiony uproszczonym modelem administracyjnym z jednym niejawnym kontem administratora. Wdrożenia serwerowe (JVM/Linux) mogą korzystać z pełnego modelu ról opisanego w §2–§4.

---

# 6. Wymagania dla Core

Core udostępnia model uwierzytelniania, autoryzacji i audytu. Wszystkie operacje administracyjne są rejestrowane.

---

# 7. Dokumenty powiązane

- 00-Foundation/03-Model-domenowy.md
- 00-Foundation/06-Glossary.md
- 30-Infrastructure/31-Bezpieczenstwo.md
- 40-Platforms/43-Przenosnosc.md
