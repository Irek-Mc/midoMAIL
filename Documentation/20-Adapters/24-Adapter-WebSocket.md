# midoMAIL 2.0

# Dokument 24 — Adapter WebSocket

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę adaptera WebSocket odpowiedzialnego za integrację Communication Gateway z systemami wymagającymi dwukierunkowej komunikacji w czasie rzeczywistym.

---

# 2. Odpowiedzialność

Adapter WebSocket odpowiada za:

- ustanawianie i utrzymywanie połączeń WebSocket,
- odbieranie i wysyłanie komunikatów,
- mapowanie danych pomiędzy protokołem WebSocket a GatewayMessage,
- zarządzanie sesjami połączeń,
- raportowanie stanu połączeń i zdarzeń diagnostycznych.

Adapter nie zawiera logiki biznesowej, nie wykonuje routingu i nie przetwarza reguł domenowych.

---

# 3. Założenia architektoniczne

- komunikacja z Gateway odbywa się wyłącznie przez porty,
- adapter jest niezależny od implementacji Gateway Engine,
- wszystkie komunikaty są mapowane do modelu GatewayMessage,
- obsługa połączeń nie wpływa na logikę domenową.

---

# 4. Zarządzanie połączeniami

Adapter odpowiada za inicjalizację, utrzymanie oraz zamykanie sesji WebSocket, wykrywanie utraty połączenia oraz bezpieczne ponawianie połączeń zgodnie z polityką konfiguracji.

---

# 5. Diagnostyka

Adapter publikuje informacje o stanie sesji, liczbie aktywnych połączeń, błędach komunikacji, opóźnieniach oraz wykorzystaniu zasobów.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 12-GatewayMessage
- 18-Porty
- 19-Plugin-SDK
- 31-Bezpieczenstwo
- 35-Health-Monitor
