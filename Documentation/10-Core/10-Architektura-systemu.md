# midoMAIL 2.0

# Dokument 10 — Architektura systemu

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument opisuje architekturę wysokiego poziomu platformy midoMAIL 2.0. Stanowi punkt odniesienia dla wszystkich dokumentów znajdujących się w katalogu Core i definiuje odpowiedzialność głównych komponentów Communication Gateway.

---

# 2. Architektura logiczna

Rdzeń systemu jest niezależny od platform, transportów i adapterów. Komunikacja z otoczeniem odbywa się wyłącznie poprzez stabilne kontrakty.

Warstwy architektury:

- Domain (model domenowy i reguły biznesowe),
- Application (koordynacja procesów),
- Core Services (routing, planowanie, przetwarzanie),
- Ports (kontrakty),
- Adapters (integracja z transportami),
- Platform (Android, JVM, Linux).

---

# 3. Główne komponenty

Rdzeń platformy składa się z następujących komponentów:

- Gateway Engine,
- GatewayMessage,
- Routing Engine,
- Adapter Registry,
- Event Bus,
- Scheduler,
- Exactly Once Engine,
- Porty systemowe,
- Plugin SDK.

Każdy komponent posiada własny dokument opisujący architekturę, odpowiedzialność oraz kontrakty.

---

# 4. Zasady współpracy komponentów

Komponenty komunikują się wyłącznie poprzez kontrakty. Żaden komponent nie może uzależniać swojej logiki od konkretnego adaptera, transportu ani platformy uruchomieniowej.

---

# 5. Zasady rozwoju architektury

Architektura rozwijana jest zgodnie z zasadami:

- pojedynczej odpowiedzialności,
- niskiego sprzężenia,
- wysokiej spójności,
- odwrócenia zależności,
- rozszerzalności bez modyfikacji rdzenia.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md

Dokument stanowi punkt wejścia do dokumentacji Core. Szczegóły poszczególnych komponentów zostały opisane w dokumentach 11–19.