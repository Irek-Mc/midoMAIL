# midoMAIL 2.0

# Dokument 19 — Plugin SDK

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę Plugin SDK umożliwiającego rozszerzanie platformy midoMAIL 2.0 poprzez tworzenie nowych adapterów, integracji oraz komponentów zgodnych z kontraktami Communication Gateway.

---

# 2. Założenia

Plugin SDK stanowi jedyny oficjalny mechanizm rozszerzania platformy. Rozszerzenia nie mogą wymagać modyfikacji Gateway Engine ani pozostałych komponentów rdzenia.

---

# 3. Cele projektowe

Plugin SDK powinien:

- udostępniać stabilne kontrakty,
- izolować implementacje od rdzenia,
- umożliwiać niezależny rozwój adapterów,
- wspierać wersjonowanie kontraktów,
- zapewniać zgodność z architekturą Core.

---

# 4. Zakres rozszerzeń

SDK umożliwia implementację między innymi:

- adapterów komunikacyjnych,
- dostawców usług infrastrukturalnych,
- strategii routingu,
- polityk planowania,
- mechanizmów diagnostycznych,
- rozszerzeń administracyjnych.

---

# 5. Wymagania

Każde rozszerzenie powinno:

- implementować wyłącznie publiczne kontrakty,
- deklarować obsługiwane możliwości,
- być niezależne od platformy, jeśli nie jest to wymagane,
- udostępniać informacje diagnostyczne,
- współpracować z mechanizmami monitorowania i bezpieczeństwa.

---

# 6. Cykl życia pluginu

Plugin przechodzi przez etapy:

- wykrycie,
- weryfikacja zgodności,
- rejestracja,
- inicjalizacja,
- uruchomienie,
- monitorowanie,
- zatrzymanie,
- wyrejestrowanie.

Konkretny interfejs (`Adapter`, `Capability`, `AdapterFactory`), mechanizm deklaracji możliwości oraz przepływ rejestracji/konfiguracji/DI odpowiadające tym etapom są zdefiniowane w 91-Specification/SPEC-0010-Plugin-SDK-Contract.md.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 14-Registry-Adapterow
- 18-Porty
- 20-Transporty
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
