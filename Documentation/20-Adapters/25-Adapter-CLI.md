# midoMAIL 2.0

# Dokument 25 — Adapter CLI

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę adaptera CLI odpowiedzialnego za integrację Communication Gateway z interfejsem wiersza poleceń. Adapter umożliwia administrację, diagnostykę oraz integrację z narzędziami automatyzującymi.

---

# 2. Odpowiedzialność

Adapter CLI odpowiada za:

- przyjmowanie poleceń użytkownika,
- mapowanie poleceń na operacje Gateway,
- prezentowanie wyników zgodnie z kontraktem CLI,
- obsługę skryptów i trybu wsadowego,
- raportowanie zdarzeń diagnostycznych.

Adapter nie zawiera logiki biznesowej i nie realizuje przetwarzania domenowego.

---

# 3. Założenia architektoniczne

- komunikacja z Gateway odbywa się wyłącznie przez porty,
- interfejs CLI jest niezależny od implementacji Gateway Engine,
- polecenia administracyjne są walidowane przed przekazaniem do rdzenia,
- format wyjściowy może być rozszerzany bez zmian w Gateway.

---

# 4. Zakres funkcjonalny

Adapter powinien umożliwiać:

- diagnostykę systemu,
- monitorowanie stanu komponentów,
- wykonywanie operacji administracyjnych,
- eksport danych diagnostycznych,
- integrację z narzędziami DevOps.

---

# 5. Diagnostyka

Adapter publikuje informacje o wykonanych poleceniach, błędach, czasie wykonania oraz stanie komunikacji z Gateway.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 18-Porty
- 19-Plugin-SDK
- 33-Logowanie
- 36-Diagnostyka
- 53-Dokumentacja-administratora
