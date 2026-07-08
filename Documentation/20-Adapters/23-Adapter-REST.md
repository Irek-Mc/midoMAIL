# midoMAIL 2.0

# Dokument 23 — Adapter REST

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę adaptera REST odpowiedzialnego za integrację Communication Gateway z systemami zewnętrznymi wykorzystującymi interfejs HTTP/REST.

---

# 2. Odpowiedzialność

Adapter REST odpowiada za:

- udostępnianie punktów końcowych API,
- odbieranie żądań i przekazywanie ich do Gateway,
- mapowanie danych pomiędzy formatem HTTP a GatewayMessage,
- zwracanie odpowiedzi zgodnych z kontraktem API,
- raportowanie zdarzeń diagnostycznych.

Adapter nie zawiera logiki biznesowej, nie podejmuje decyzji routingu i nie realizuje przetwarzania domenowego.

---

# 3. Założenia architektoniczne

- komunikacja z Gateway odbywa się wyłącznie poprzez porty,
- kontrakty API są niezależne od implementacji Gateway Engine,
- wszystkie komunikaty są mapowane do modelu GatewayMessage,
- uwierzytelnianie i autoryzacja są realizowane zgodnie z polityką bezpieczeństwa platformy.

---

# 4. Integracja

Adapter odpowiada za obsługę protokołu HTTP, serializację danych, walidację wejścia oraz translację błędów infrastrukturalnych na odpowiedzi zgodne z kontraktem API.

---

# 5. Diagnostyka

Adapter publikuje metryki dotyczące żądań, czasu odpowiedzi, błędów, obciążenia oraz dostępności interfejsu REST.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 12-GatewayMessage
- 18-Porty
- 19-Plugin-SDK
- 31-Bezpieczenstwo
- 33-Logowanie
