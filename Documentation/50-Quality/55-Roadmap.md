# midoMAIL 2.0

# Dokument 55 — Roadmap

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje kolejność realizacji platformy midoMAIL 2.0. Kolejność wynika z zasady „Core First" (01-Wizja-produktu.md, §6) oraz z ryzyk zidentyfikowanych podczas budowy wersji 1.x — każda faza domyka jedno konkretne ryzyko, zanim rozpocznie się kolejna. UI powstaje po adapterach, których jest klientem (README.md, §Zasady; 60-User-Interface/60-UX-Filozofia.md), nigdy przed Core.

---

# 2. Faza 0 — Domknięcie dokumentacji

Warunek wejścia do implementacji.

- Zgodność `ExternalReference`/`SourceEventId` między dokumentami narracyjnymi (10-Core) a specyfikacją techniczną (91-Specification) — w tym SPEC-0001, SPEC-0006, SPEC-0008.
- Zgodność modelu reguł routingu (RuleId/Priority/Enabled/Conditions/Version) między 13-Routing.md a SPEC-0007-Routing-Contract.md.
- Kontrakt Message Store (query API, schemat, retention policy, performance targets, atomowość) zdefiniowany przed implementacją — SPEC-0009-Message-Store-Contract.md.
- Kontrakt Plugin SDK (interfejs Adapter, model Capability, przepływ rejestracji/konfiguracji, mechanizm DI) zdefiniowany przed implementacją pierwszego adaptera — SPEC-0010-Plugin-SDK-Contract.md.
- Schemat konfiguracji (format YAML, przykładowy plik, typy/domyślne/zakresy, walidacja krzyżowa) zdefiniowany przed implementacją UI Konfiguracja — SPEC-0005-Configuration-Model.md, ADR-0004-Format-Konfiguracji.md.
- MessagePriority w modelu GatewayMessage (zamiast dodawania po fakcie jako breaking change) — ADR-0005-Message-Priority.md.
- Architektura Rate Limiting (zakres per adapter/operacja, zachowanie po przekroczeniu, współpraca z Exactly Once) zdefiniowana przed implementacją adapterów — ADR-0006-Rate-Limiting.md, SPEC-0011-Rate-Limiting-Contract.md.
- Model dostarczania powiadomień (kanały, routing wg poziomu, eskalacja) zdefiniowany przed implementacją Health Monitor — ADR-0007-Dostarczanie-Powiadomien.md, 30-Infrastructure/38-Powiadomienia.md.
- Zmiana statusu kluczowych dokumentów Core i Specification z „Draft" na „Accepted".
- Jeden słownik pojęć (06-Glossary.md) jako jedyne źródło definicji terminów — bez duplikatów.
- Brak koncepcji End-User/Contact (00-Foundation/03-Model-domenowy.md, §7) oraz brak wielodzierżawności (ADR-0008-Multi-Tenancy.md) — jawnie zapisane jako świadome decyzje, nie przeoczenia.
- Backup i odtwarzanie Message Store, w tym ryzyko związane z Exactly Once (30-Infrastructure/32-Baza-danych.md, §6).
- Obcięcie treści przekraczającej limit Multipart SMS oraz zachowanie Adaptera GSM przy braku zasięgu (ADR-0009-Obciecie-Tresci-SMS.md, 20-Adapters/21-Adapter-GSM.md, §9).

**Kryterium wyjścia:** brak rozbieżności między dokumentami narracyjnymi a specyfikacją techniczną; brak zdublowanych pojęć.

---

# 3. Faza 1 — Core (bez adapterów, bez platformy, bez UI)

Rdzeń musi istnieć i być w pełni przetestowany, zanim powstanie jakikolwiek adapter lub interfejs.

Zakres:

- Model domenowy: GatewayMessage (Identity + ExternalReference + MessagePriority), Processing State, Event Model.
- Gateway Engine: koordynacja, walidacja, publikacja zdarzeń — bez logiki transportowej.
- Porty: MessageStore (zgodnie z SPEC-0009 — query API, atomowy `insertIfAbsent`, retention), EventPublisher, ConfigurationProvider, SchedulerProvider, HealthProvider — same kontrakty, implementacje testowe (in-memory).
- Routing Engine — model reguł (RuleId, Priority, Enabled, Conditions, TargetChannel, TargetAdapter, DeliveryPolicy, SetPriority, Version).
- Registry Adapterów — szkielet cyklu życia (Registered → Ready → Stopped), implementujący przepływ rejestracji z SPEC-0010-Plugin-SDK-Contract.md.
- Rate Limiter — port token bucket per adapter/operacja, zintegrowany ze Schedulerem jako mechanizm backpressure (SPEC-0011-Rate-Limiting-Contract.md).
- Exactly Once Engine — weryfikacja duplikatu na podstawie `ExternalReference`, zanim powstanie nowy `GatewayMessage`.

Testy: wyłącznie JVM, bez Androida, bez Robolectric (50-Testy, §5).

**Kryterium wyjścia:** Gateway Engine potrafi przyjąć, zweryfikować pod kątem Exactly Once, zroutować i opublikować zdarzenie dla syntetycznego komunikatu — bez jednego rzeczywistego adaptera i bez UI.

---

# 4. Faza 2 — Adapter Email (JVM, bez Androida)

Email jest pierwszym adapterem, ponieważ SMTP/IMAP dają się w pełni zweryfikować na czystym JVM (serwer testowy oraz prawdziwe konto Gmail), bez zależności od urządzenia z Androidem. Pozwala to domknąć cały łańcuch Core → Adapter → Exactly Once, zanim dojdą do głosu ograniczenia platformy Android.

Zakres:

- Wysyłka SMTP, odbiór IMAP z rozróżnieniem IMAPS/STARTTLS na podstawie portu i konfiguracji TLS.
- `ExternalReference` = nagłówek `Message-ID` (RFC 5322); wątkowanie przez `In-Reply-To`/`References`.
- Jawna obsługa semantyki Gmail: `READ_ONLY` nie ustawia `\Seen` — deduplikacja nie może polegać na tej fladze.
- Test regresyjny odtwarzający scenariusz podwójnego przetworzenia tej samej odpowiedzi IMAP (bezpośrednia lekcja z wersji 1.x) — musi kończyć się jednym, a nie dwoma wywołaniami dostarczenia.

**Kryterium wyjścia:** ta sama odpowiedź e-mail, odebrana dwukrotnie przez IMAP (np. przez brak `\Seen`), prowadzi dokładnie do jednego dostarczenia w Gateway.

---

# 5. Faza 3 — Platforma Android + Adapter GSM

Najbardziej ryzykowna faza — tu leżały wszystkie niezdiagnozowane błędy wersji 1.x.

Zakres:

- Platforma Android: Foreground Service dla pracy 24/7, model uprawnień runtime (SEND_SMS/RECEIVE_SMS), Android Keystore dla sekretów.
- **Rejestracja midoMAIL jako domyślnej aplikacji SMS/MMS jest warunkiem wstępnym tej fazy, nie decyzją odroczoną** (ADR-0003, 40-Platforms/40-Android.md §6) — bez tej roli odbiór MMS nie jest gwarantowany przez platformę, a pola SMS Provider (date_sent/protocol/service_center) pozostają niekompletne.
- Adapter GSM — SMS: wysyłka wyłącznie przez `sendMultipartTextMessage` (nigdy `sendTextMessage` dla treści przekraczającej limit segmentu), potwierdzenie przez `PendingIntent SENT`/`DELIVERED` jako jedyne źródło prawdy o dostarczeniu — brak wyjątku nie oznacza sukcesu. Obsługa wielu kart SIM jako opcjonalna możliwość adaptera.
- Adapter GSM — MMS: odbiór i wysyłka MMS jako odrębny transport, mapowanie załączników na model Payload (91-Specification/SPEC-0001-GatewayMessage.md, §Payload).
- Weryfikacja pełnego łańcucha SMS → Email → odpowiedź → SMS na rzeczywistym urządzeniu, oraz MMS ze zdjęciem → Email z załącznikiem (nie linkiem) → odpowiedź → SMS/MMS.

**Kryterium wyjścia:** midoMAIL działa jako domyślna aplikacja SMS/MMS na urządzeniu testowym; wiadomość dłuższa niż jeden segment SMS dociera do odbiorcy w całości; MMS ze zdjęciem dociera do Gateway i zostaje przekazany dalej jako e-mail z rzeczywistym załącznikiem; Gateway otrzymuje potwierdzenie dostarczenia zgodne ze stanem faktycznym (nie tylko brak wyjątku).

---

# 6. Faza 4 — Infrastruktura wspierająca

- Scheduler — automatyczne, cykliczne sprawdzanie nowych komunikatów (w wersji 1.x nigdy niezaimplementowany; sprawdzanie było wyłącznie ręczne).
- Health Monitor (w tym model Alert/Health), Diagnostyka, Statystyki.
- Powiadomienia — kanały Email/Push/Webhook, routing wg poziomu Alertu, eskalacja przy braku potwierdzenia (30-Infrastructure/38-Powiadomienia.md); integracja z PagerDuty/OpsGenie/Slack wyłącznie przez kanał Webhook, bez dedykowanych klientów.
- Logowanie, Error Handling zgodnie z zasadą „błąd nie może zostać ukryty" — throttling raportowany odrębnie od błędów (34-Error-Handling.md, §6).

**Kryterium wyjścia:** Gateway działa 24/7 bez ręcznej interwencji, a stan systemu jest w pełni obserwowalny poprzez kontrakty Core; alert krytyczny dociera do skonfigurowanego kanału zewnętrznego i eskaluje się, jeśli pozostaje niepotwierdzony.

---

# 7. Faza 5 — Adapter REST i Adapter CLI

REST i CLI powstają zaraz po domknięciu infrastruktury wspierającej, ponieważ są jednocześnie: (a) dowodem, że Plugin SDK i Registry Adapterów rzeczywiście nie wymagają zmian w Core przy dodaniu adaptera, oraz (b) warunkiem wstępnym Fazy 6 — UI jest wyłącznie klientem tych dwóch adapterów (README.md, §Zasady) i nie może powstać wcześniej.

**Kryterium wyjścia:** Adapter REST i Adapter CLI udostępniają pełny zestaw operacji administracyjnych (odczyt stanu, zarządzanie adapterami, konfiguracja, routing) opisanych w SPEC-0006, bez modyfikacji Gateway Engine.

---

# 8. Faza 6 — User Interface

UI nie jest adapterem — jest klientem Adaptera REST lub Adaptera CLI (60-UX-Filozofia.md, §Integracja z architekturą) i dlatego nie może powstać przed Fazą 5.

Zakres, w kolejności zależności:

1. Dashboard, Monitoring, Diagnostyka, Logi, Statystyki — wyłącznie odczyt, najniższe ryzyko.
2. Komunikaty — odczyt i ślad przetwarzania; „Ponowne przetworzenie" jako jawnie audytowana operacja administracyjna, zgodna z polityką Exactly Once (nie: obejście jej).
3. Routing, Adaptery, Konfiguracja — operacje zapisu z walidacją i symulacją przed zastosowaniem.
4. Użytkownicy i uprawnienia — pełny model wieloużytkownikowy tylko dla profili wdrożenia serwerowego (JVM/Linux); na Androidzie uproszczony model jednego administratora.
5. Widoki mobilne — na końcu, jako okrojony klient tych samych kontraktów co pełny panel.

**Kryterium wyjścia:** każda wartość prezentowana w UI pochodzi z jawnego kontraktu Core, żadna nie jest wyliczana ani rekonstruowana lokalnie przez UI.

---

# 9. Faza 7 — Kolejne adaptery i przenośność

- Adapter WebSocket jako kolejny transport po REST/CLI.
- Uruchomienie Core na czystym JVM/Linux, bez Androida, jako dowód przenośności zadeklarowanej w 43-Przenosnosc.
- Ocena wariantu Adaptera GSM opartego o modem USB/AT-command (Raspberry Pi, Linux) jako alternatywy dla Android SmsManager.

**Kryterium wyjścia:** identyczny model domenowy i Gateway Engine, bez zmian, uruchomiony na drugiej platformie; nowy adapter dodany bez modyfikacji Gateway Engine, Routing Engine ani modelu domenowego.

---

# 10. Zasady ogólne obowiązujące w każdej fazie

- Żadna faza nie rozpoczyna się, dopóki poprzednia nie spełni swojego kryterium wyjścia.
- UI nigdy nie poprzedza Core ani adapterów, których jest klientem — to nadrzędne ograniczenie kolejności wynikające z zasady Core First.
- Każde odstępstwo od dokumentacji wymaga ADR przed implementacją, nie po.
- Każdy błąd odkryty podczas danej fazy skutkuje testem regresyjnym w tej samej fazie, nie odłożeniem na później.

---

# 11. Dokumenty powiązane

- 00-Foundation/01-Wizja-produktu.md
- 00-Foundation/06-Glossary.md
- 10-Core/17-Exactly-Once.md
- 20-Adapters/21-Adapter-GSM.md
- 20-Adapters/22-Adapter-Email.md
- 40-Platforms/40-Android.md
- 50-Quality/50-Testy.md
- 60-User-Interface/60-UX-Filozofia.md
- 30-Infrastructure/38-Powiadomienia.md
- 90-ADR/ADR-0003-Domyslna-aplikacja-SMS-MMS.md
- 90-ADR/ADR-0004-Format-Konfiguracji.md
- 90-ADR/ADR-0005-Message-Priority.md
- 90-ADR/ADR-0006-Rate-Limiting.md
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
- 90-ADR/ADR-0008-Multi-Tenancy.md
- 90-ADR/ADR-0009-Obciecie-Tresci-SMS.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md
- 92-Review/REVIEW-0001-Architektura-v2.0.md
