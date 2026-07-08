# midoMAIL 2.0

# Dokument 18 — Porty

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje kontrakty architektoniczne (Porty) wykorzystywane przez rdzeń Communication Gateway do współpracy z adapterami oraz komponentami infrastrukturalnymi. Porty są jedynym dopuszczalnym sposobem komunikacji pomiędzy domeną a światem zewnętrznym.

---

# 2. Założenia architektoniczne

- wszystkie zależności są kierowane do wnętrza systemu,
- Gateway Engine komunikuje się wyłącznie poprzez porty,
- port definiuje kontrakt, a nie implementację,
- implementacje portów znajdują się poza rdzeniem domenowym,
- kontrakty są stabilne, wersjonowane i zgodne z zasadą Open/Closed.

---

# 3. Kategorie portów

Platforma rozróżnia następujące grupy portów:

- porty wejściowe (Input Ports),
- porty wyjściowe (Output Ports),
- porty infrastrukturalne,
- porty administracyjne,
- porty rozszerzeń (Plugin SDK).

---

# 4. Podstawowe kontrakty

Architektura przewiduje między innymi porty odpowiedzialne za:

- odbiór komunikatów,
- dostarczanie komunikatów,
- trwałość danych i Message Store — pełny kontrakt: 91-Specification/SPEC-0009-Message-Store-Contract.md (query API, schemat, retention policy, performance targets, atomowość dla Exactly Once),
- publikację zdarzeń,
- planowanie zadań — pełny kontrakt: 91-Specification/SPEC-0013-Scheduler-Provider-Contract.md,
- ograniczanie przepustowości (Rate Limiter) — pełny kontrakt: 91-Specification/SPEC-0011-Rate-Limiting-Contract.md,
- monitorowanie stanu,
- konfigurację — pełny kontrakt: 91-Specification/SPEC-0012-Configuration-Provider-Contract.md,
- diagnostykę,
- przechowywanie danych binarnych załączników (bez gwarancji trwałości) — pełny kontrakt: 91-Specification/SPEC-0016-Attachment-Store-Contract.md, ADR-0013-Attachment-Store.md,
- bezpieczne przechowywanie sekretów (Secret Store) — pełny kontrakt: 91-Specification/SPEC-0017-Secret-Store-Contract.md.

Szczegółowe kontrakty pozostałych portów zostaną zdefiniowane w dokumentacji Specification (SPEC-0002-Porty.md, §Plan dalszej specyfikacji).

---

# 5. Wymagania

Każdy port powinien:

- posiadać jednoznaczny zakres odpowiedzialności,
- być niezależny od platformy,
- umożliwiać testowanie z wykorzystaniem implementacji zastępczych,
- zachowywać zgodność wsteczną lub być wersjonowany.

---

# 6. Relacja z Exactly Once

Porty odpowiedzialne za trwałość komunikatów i zmianę ich stanu muszą wspierać wymagania mechanizmu Exactly Once Processing oraz zapewniać atomowość operacji wymaganych przez architekturę.

---

# 7. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 11-Gateway-Engine
- 12-GatewayMessage
- 17-Exactly-Once
- 19-Plugin-SDK
- 90-ADR/ADR-0006-Rate-Limiting.md
- 91-Specification/SPEC-0002-Porty.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md
- 91-Specification/SPEC-0012-Configuration-Provider-Contract.md
- 91-Specification/SPEC-0013-Scheduler-Provider-Contract.md
- 90-ADR/ADR-0013-Attachment-Store.md
- 91-Specification/SPEC-0016-Attachment-Store-Contract.md
- 91-Specification/SPEC-0017-Secret-Store-Contract.md
