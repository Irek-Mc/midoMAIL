# midoMAIL 2.0

# Dokument 03 — Model domenowy

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje podstawowy model domenowy platformy midoMAIL 2.0. Opisuje pojęcia biznesowe wykorzystywane przez Communication Gateway oraz relacje pomiędzy nimi. Model domenowy stanowi fundament architektury i jest niezależny od platform, adapterów oraz technologii implementacji.

---

# 2. Zasady modelu domenowego

- Communication Gateway operuje wyłącznie na modelu domenowym.
- Model nie zawiera pojęć specyficznych dla transportów (SMS, E-mail, REST itp.).
- Adaptery tłumaczą dane zewnętrzne na model domenowy i odwrotnie.
- Reguły biznesowe są definiowane wyłącznie w domenie.

---

# 3. Główne pojęcia domenowe

Model domenowy opiera się na następujących pojęciach:

- Communication – proces wymiany komunikatów,
- Message – kanoniczny komunikat przetwarzany przez Communication Gateway,
- Channel – logiczny kanał komunikacji,
- Adapter – komponent integrujący kanał z Communication Gateway,
- Endpoint – punkt wejścia lub wyjścia komunikacji,
- Route – reguła określająca sposób dostarczenia komunikatu,
- Processing Context – kontekst przetwarzania komunikatu,
- Processing State – aktualny stan przetwarzania.

---

# 4. Granice odpowiedzialności

Model domenowy opisuje znaczenie danych oraz reguły biznesowe. Nie określa sposobu przechowywania danych, komunikacji sieciowej, implementacji adapterów ani mechanizmów infrastrukturalnych.

---

# 5. Przepływ domenowy

Komunikat wpływa do Communication Gateway poprzez adapter źródłowy, zostaje zamieniony na model domenowy, podlega walidacji, routowaniu i przetwarzaniu, a następnie jest przekazywany do odpowiedniego adaptera docelowego. Wszystkie decyzje podejmowane są na podstawie modelu domenowego i konfiguracji, nigdy na podstawie konkretnego transportu.

---

# 6. Rozszerzalność modelu

Model domenowy został zaprojektowany tak, aby dodanie nowego kanału komunikacyjnego lub adaptera nie wymagało jego modyfikacji. Rozszerzenia są realizowane poprzez nowe implementacje kontraktów, przy zachowaniu niezmienności rdzenia domeny.

---

# 7. Brak pojęcia End-User/Contact

Model domenowy celowo **nie zawiera** encji reprezentującej osobę korespondującą z Gateway (np. „Kontakt", „Klient", „End-User"). Nadawca i odbiorca komunikatu są opisywani wyłącznie jako adres w ramach Channel (Source/Destination) — bez konta, profilu ani trwałej tożsamości agregującej wiele konwersacji w czasie. Jest to świadoma decyzja, spójna z wizją produktu (00-Foundation/01-Wizja-produktu.md: midoMAIL nie jest komunikatorem ani archiwum wiadomości) — Gateway nie pełni roli CRM.

Pojęcie „użytkownik" w pozostałej dokumentacji (60-User-Interface/70-Uzytkownicy-i-uprawnienia.md) dotyczy wyłącznie operatorów/administratorów Gateway, nigdy korespondentów zewnętrznych.

---

# 8. Dokumenty powiązane
- 00-Foundation/01-Wizja-produktu.md
- 06-Glossary
- 60-User-Interface/70-Uzytkownicy-i-uprawnienia.md
- 90-ADR/ADR-0008-Multi-Tenancy.md

Szczegółowe definicje komunikatu, routingu, kontraktów, adapterów i mechanizmu Exactly Once zostały opisane w dokumentach katalogu 10-Core.