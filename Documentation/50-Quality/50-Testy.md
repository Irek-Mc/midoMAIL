# midoMAIL 2.0

# Dokument 50 — Testy

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje strategię testowania platformy midoMAIL 2.0 oraz wymagania jakościowe dla wszystkich komponentów Communication Gateway.

---

# 2. Założenia

- testowanie jest integralną częścią procesu wytwarzania,
- architektura jest projektowana pod testowalność,
- Core może być testowany bez zależności od platformy Android,
- testy stanowią element akceptacji zmian.

---

# 3. Rodzaje testów

- jednostkowe,
- integracyjne,
- kontraktowe,
- architektoniczne,
- wydajnościowe,
- regresyjne,
- end-to-end.

---

# 4. Wymagania

Każdy komponent powinien posiadać zestaw testów odpowiadający jego odpowiedzialności. Implementacje adapterów muszą być testowane niezależnie od rdzenia.

---


# 5. Zasady testowania

- Rdzeń (Core) jest testowany bez Robolectric.
- Preferowane są testy na czystym Kotlinie z atrapami interfejsów platformowych.
- Testy integracyjne powinny wykorzystywać rzeczywiste usługi i protokoły (np. serwery IMAP/SMTP) tam, gdzie daje to większą wiarygodność niż mocki.
- Każdy wykryty błąd produkcyjny powinien skutkować dodaniem testu regresyjnego.

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 18-Porty
- 43-Przenosnosc
- 51-Standard-kodowania
- 52-Deployment