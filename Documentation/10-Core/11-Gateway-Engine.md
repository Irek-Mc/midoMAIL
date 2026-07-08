# midoMAIL 2.0

# Dokument 11 — Gateway Engine

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę, odpowiedzialność oraz granice komponentu Gateway Engine. Gateway Engine jest centralnym komponentem platformy midoMAIL 2.0 i jedynym miejscem realizacji logiki biznesowej Communication Gateway.

---

# 2. Odpowiedzialność

Gateway Engine odpowiada za:

- koordynację przetwarzania komunikatów,
- egzekwowanie reguł biznesowych,
- współpracę z Routing Engine,
- współpracę z mechanizmem Exactly Once,
- publikowanie zdarzeń domenowych,
- zarządzanie cyklem życia przetwarzania.

Gateway Engine nie odpowiada za komunikację z transportami, dostęp do platformy, przechowywanie danych ani implementację adapterów.

---

# 3. Granice komponentu

Wejścia:

- GatewayMessage odebrany przez port wejściowy,
- zdarzenia domenowe,
- wyniki działania usług infrastrukturalnych udostępnionych przez porty.

Wyjścia:

- decyzje dotyczące routingu,
- zdarzenia domenowe,
- polecenia dla adapterów poprzez porty,
- informacje o stanie przetwarzania.

---

# 4. Zależności

Gateway Engine może zależeć wyłącznie od:

- modelu domenowego,
- kontraktów portów,
- usług domenowych.

Nie może zależeć od adapterów, Androida, baz danych ani bibliotek infrastrukturalnych.

---

# 5. Przepływ przetwarzania

1. Odebranie komunikatu przez port wejściowy.
2. Walidacja modelu domenowego.
3. Utworzenie kontekstu przetwarzania.
4. Weryfikacja polityki Exactly Once.
5. Wyznaczenie trasy przez Routing Engine.
6. Publikacja zdarzeń domenowych.
7. Przekazanie komunikatu do portu wyjściowego.
8. Aktualizacja stanu przetwarzania.

---

# 6. Punkty rozszerzeń

Gateway Engine nie jest rozszerzany poprzez modyfikację kodu. Rozszerzenia realizowane są przez implementację kontraktów, konfigurację polityk oraz dodawanie adapterów zgodnych z Plugin SDK.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 10-Architektura-systemu
- 12-GatewayMessage
- 13-Routing
- 17-Exactly-Once
- 18-Porty
- 19-Plugin-SDK
