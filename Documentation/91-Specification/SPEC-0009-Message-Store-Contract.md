# SPEC-0009 — Message Store Contract

**Status:** Accepted
**Powiązane dokumenty:** 10-Core/18-Porty.md, 10-Core/17-Exactly-Once.md, 30-Infrastructure/32-Baza-danych.md

---

# Cel

Dokument definiuje techniczny kontrakt portu Message Store — jedynego miejsca trwałego przechowywania GatewayMessage. Jest pierwszą z indywidualnych specyfikacji portów zapowiedzianych w SPEC-0002-Porty.md, §Plan dalszej specyfikacji. Powstał w odpowiedzi na lukę: warstwa UI (60-User-Interface/62-Komunikaty.md, 67-Diagnostyka.md, 68-Statystyki.md) zakłada możliwości wyszukiwania, filtrowania i agregacji, których żaden dotychczasowy dokument formalnie nie obiecywał — bez tego kontraktu UI byłoby projektowane „na ślepo", a implementacja Message Store nie wiedziałaby, co musi udostępnić.

---

# Założenia

- Message Store jest jedynym źródłem prawdy o GatewayMessage i historii jego przetwarzania.
- Kontrakt jest niezależny od technologii bazy danych (30-Infrastructure/32-Baza-danych.md, §2).
- Operacja zapisu nowego komunikatu na podstawie ExternalReference musi być atomowa (patrz §Atomowość i Exactly Once).
- Message Store nie interpretuje treści Payload ani Attachments — przechowuje je i indeksuje zgodnie z niniejszym kontraktem, bez logiki biznesowej.

---

# Query API

Minimalny zestaw operacji odczytu:

- `findById(MessageId): GatewayMessage?`
- `findByExternalReference(ExternalReference): GatewayMessage?` — wykorzystywane przez Exactly Once przed utworzeniem nowego GatewayMessage (10-Core/17-Exactly-Once.md).
- `findByCorrelationId(CorrelationId): List<GatewayMessage>` — pełna historia jednego procesu biznesowego (np. SMS + odpowiadający e-mail).
- `query(filter, sort, page): Page<GatewayMessage>` — ogólne wyszukiwanie, wymagane przez 62-Komunikaty i 67-Diagnostyka.

## Filtry (query)

- Kanał (źródłowy/docelowy),
- AdapterId (źródłowy/docelowy),
- Processing State,
- MessagePriority,
- zakres czasu (CreatedAt/UpdatedAt),
- CorrelationId,
- ExternalReference,
- pełnotekstowe wyszukiwanie we `Payload.Content` (metadane Attachments — nazwa pliku, typ MIME — są przeszukiwalne; zawartość binarna załącznika nie podlega indeksowaniu pełnotekstowemu),
- `channelAddress` — dopasowuje `Channel.address` źródła LUB celu (np. numer telefonu, adres e-mail); wprowadzone, by SMS mógł odnaleźć swoją istniejącą korelację po numerze nadawcy, gdy protokół nie niesie natywnego wątkowania (ADR-0039-Watkowanie-SMS-Poprzez-Numer-Telefonu.md).

## Paginacja

Paginacja kursorowa (cursor-based), nie offsetowa — Message Store jest w praktyce logiem zdarzeń z ciągłym dopisywaniem nowych rekordów; paginacja offsetowa przesuwa się i gubi/duplikuje wyniki pod obciążeniem zapisu. Kursor oparty o (CreatedAt, MessageId) jako klucz stabilny.

## Sortowanie

Domyślnie malejąco po CreatedAt. Dodatkowo dopuszczalne sortowanie po UpdatedAt (dla widoku „ostatnio zaktualizowane" w 61-Dashboard.md) oraz po MessagePriority malejąco (zgodnie z kolejkowaniem w Schedulerze — 10-Core/16-Scheduler.md, §5; ADR-0005-Message-Priority.md).

---

# Schemat — co jest indeksowane

Wymagane indeksy:

- MessageId — unikalny, klucz główny.
- ExternalReference — unikalny, warunek wymagany przez Exactly Once (musi wspierać szybkie sprawdzenie istnienia przed zapisem).
- CorrelationId — indeks (nie unikalny; wiele komunikatów należy do jednego procesu).
- Channel (Source, Destination) — indeks, wykorzystywany przez filtry UI i Routing diagnostykę.
- AdapterId (Source, Destination) — indeks.
- Processing State — indeks, wykorzystywany przez Dashboard i Diagnostykę.
- MessagePriority — indeks, wykorzystywany przez kolejkowanie w Schedulerze oraz filtry UI.
- CreatedAt, UpdatedAt — indeks, wykorzystywany przez sortowanie, paginację kursorową i zakresy czasu.
- Payload.Content — indeks pełnotekstowy (FTS).
- Attachments — metadane (ContentType, FileName, Size) indeksowane; dane binarne — nie.

Dane binarne Attachments nie są traktowane jako trwałe dane biznesowe Message Store (10-Core/12-GatewayMessage.md, §8) — przechowywane wyłącznie na czas przetwarzania komunikatu, zgodnie z zasadą minimalizacji danych (30-Infrastructure/31-Bezpieczenstwo.md).

---

# Retention policy

- Domyślny okres retencji pełnej treści komunikatu (Payload, w tym Attachments) jest konfigurowalny (30-Infrastructure/30-Konfiguracja.md) — proponowana wartość domyślna: 30 dni.
- Po upływie okresu retencji treść komunikatu (Payload) podlega archiwizacji (eksport) lub trwałemu usunięciu (purge), zgodnie z konfiguracją wdrożenia.
- **Rekord deduplikacji (ExternalReference → MessageId) jest przechowywany niezależnie od retencji treści** i przeżywa purge Payload — bez tego Exactly Once przestałoby chronić przed ponownym przetworzeniem starych zdarzeń źródłowych po wygaśnięciu retencji treści. Rekord deduplikacji ma własny, dłuższy lub nieograniczony okres życia, konfigurowalny osobno.
- Zagregowane dane statystyczne (30-Infrastructure/37-Statystyki.md) przeżywają purge danych źródłowych — agregaty są liczone i utrwalane przed usunięciem surowych komunikatów, nie liczone „na żądanie" z danych, które mogły już zostać wyczyszczone.
- Operacja purge/archiwizacji jest audytowalna i publikuje zdarzenie domenowe (10-Core/15-Event-Bus.md).

---

# Performance targets

Wartości orientacyjne dla typowego wdrożenia (mały/średni gateway komunikacyjny, nie systemy o skali operatorskiej) — konfigurowalne, nie sztywne:

- Skala: do ok. kilku tysięcy komunikatów dziennie, dziesiątki tysięcy przechowywanych w domyślnym oknie retencji (30 dni).
- Wyszukiwanie filtrowane + paginacja: cel — poniżej 200 ms dla typowego zapytania (95. percentyl) na zbiorze danych zgodnym ze skalą powyżej.
- `findByExternalReference` (ścieżka krytyczna Exactly Once, wywoływana synchronicznie w trakcie przetwarzania każdego komunikatu): cel — poniżej 50 ms (95. percentyl) — to zapytanie jest na krytycznej ścieżce każdego przetwarzanego komunikatu, więc ma węższy cel niż ogólne wyszukiwanie UI.
- Wartości powyżej są punktem odniesienia dla wyboru technologii przechowywania (32-Baza-danych.md, §2) — nie wymaganiem kontraktowym API.

---

# Atomowość i Exactly Once

- Operacja `insertIfAbsent(ExternalReference, GatewayMessage): InsertResult` (Inserted | AlreadyExists) musi być atomowa — sprawdzenie istnienia i zapis nie mogą być dwoma osobnymi, nie-atomowymi krokami wykonywanymi przez wywołującego (10-Core/17-Exactly-Once.md, §Minimalny model operacji, krok 2–3). To jest bezpośrednie zabezpieczenie przed błędem odtworzonym w wersji 1.x, gdzie sprawdzenie i zapis były rozdzielone i nieatomowe.
- Aktualizacja Processing State istniejącego komunikatu jest atomowa względem odczytu poprzedniego stanu (compare-and-set lub równoważny mechanizm) — zapobiega utracie równoległych aktualizacji stanu.
- Zakłada się pojedynczy proces zapisujący (single-writer) na instancję Gateway w wersji 2.0; wdrożenia wieloinstancyjne (klaster) są poza zakresem tej specyfikacji i wymagałyby osobnego ADR przed implementacją.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 10-Core/16-Scheduler.md
- 10-Core/17-Exactly-Once.md
- 10-Core/18-Porty.md
- 30-Infrastructure/30-Konfiguracja.md
- 30-Infrastructure/31-Bezpieczenstwo.md
- 30-Infrastructure/32-Baza-danych.md
- 30-Infrastructure/37-Statystyki.md
- 60-User-Interface/62-Komunikaty.md
- 60-User-Interface/67-Diagnostyka.md
- 60-User-Interface/68-Statystyki.md
- 90-ADR/ADR-0005-Message-Priority.md
- SPEC-0002-Porty.md
- SPEC-0008-Exactly-Once-Contract.md
