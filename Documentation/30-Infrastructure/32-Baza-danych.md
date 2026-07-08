# midoMAIL 2.0

# Dokument 32 — Baza danych

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje rolę warstwy trwałości danych w platformie midoMAIL 2.0 oraz wymagania architektoniczne dotyczące przechowywania informacji wykorzystywanych przez Communication Gateway.

---

# 2. Założenia architektoniczne

Warstwa trwałości stanowi element infrastruktury. Rdzeń systemu nie zależy od konkretnej technologii bazy danych ani sposobu przechowywania danych. Dostęp do trwałości odbywa się wyłącznie poprzez porty.

---

# 3. Zakres odpowiedzialności

Warstwa danych odpowiada za:

- trwałe przechowywanie stanu systemu,
- przechowywanie konfiguracji wymagającej trwałości,
- obsługę Message Store,
- dane wymagane przez Exactly Once,
- metadane diagnostyczne i administracyjne,
- migracje schematu danych.

---

# 4. Wymagania

System powinien zapewniać:

- atomowość operacji wymaganych przez architekturę,
- spójność danych,
- możliwość odtworzenia stanu po awarii,
- wersjonowanie schematu,
- kopie zapasowe i odtwarzanie,
- możliwość wymiany technologii bazy bez wpływu na domenę.

---

# 5. Ograniczenia

Warstwa danych nie implementuje logiki biznesowej, routingu ani adapterów. Odpowiada wyłącznie za trwałość informacji udostępnianą przez kontrakty portów.

---

# 6. Backup i odtwarzanie

## Co podlega backupowi

- **Message Store** — pełna zawartość (komunikaty w oknie retencji oraz rekordy deduplikacji Exactly Once).
- **Konfiguracja** — bieżąca i historyczna (30-Konfiguracja, §Zasady zmian) — wersjonowanie wewnątrz aplikacji nie zastępuje backupu zewnętrznego (np. utrata/reset urządzenia usuwa też historię wersji).
- **Sekrety** — poza zakresem backupu aplikacyjnego; magazyn sekretów (Android Keystore lub równoważny) ma własny mechanizm odtwarzania specyficzny dla platformy, poza kontrolą Gateway.

## Częstotliwość i przechowywanie

Backup wykonywany cyklicznie (zadanie Schedulera, 10-Core/16-Scheduler.md) z konfigurowalnym interwałem; kopie przechowywane poza urządzeniem/instancją Gateway (np. eksport do zewnętrznego magazynu), zgodnie z zasadą minimalizacji ryzyka utraty przy awarii pojedynczego urządzenia.

## Odtwarzanie — ryzyko związane z Exactly Once

**Przywrócenie starszej kopii Message Store może odtworzyć problem ponownego przetworzenia komunikatów**, jeśli systemy źródłowe (skrzynka IMAP, sieć GSM) poszły naprzód względem momentu wykonania backupu — rekordy deduplikacji dla zdarzeń przetworzonych po backupie zostaną utracone, a te zdarzenia mogą zostać przetworzone ponownie przy najbliższym sprawdzeniu (dokładnie mechanizm zdiagnozowany w wersji 1.x). Procedura odtwarzania musi obejmować jawną weryfikację: po przywróceniu, przed wznowieniem normalnej pracy adapterów, porównanie stanu rekordów deduplikacji z rzeczywistym stanem źródeł zewnętrznych (np. które UID/Message-ID IMAP są nadal nieprzetworzone) — nie wystarczy samo przywrócenie plików.

---

# 7. Message Store — retention

Pełny kontrakt techniczny Message Store (query API, schemat, performance targets, atomowość) definiuje 91-Specification/SPEC-0009-Message-Store-Contract.md. Warstwa danych realizuje w szczególności politykę retencji stamtąd wynikającą:

- treść komunikatu (Payload, Attachments) podlega konfigurowalnej retencji z domyślnym oknem 30 dni, po czym jest archiwizowana lub usuwana,
- rekord deduplikacji Exactly Once (ExternalReference → MessageId) ma odrębny, dłuższy okres życia niż treść komunikatu,
- zagregowane statystyki (37-Statystyki) są utrwalane przed usunięciem danych źródłowych.

---

# 8. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 10-Core/16-Scheduler.md
- 17-Exactly-Once
- 18-Porty
- 30-Konfiguracja
- 33-Logowanie
- 37-Statystyki
- 52-Deployment
- 91-Specification/SPEC-0009-Message-Store-Contract.md