# ADR-0037 — Ocena wariantu Adaptera GSM opartego o modem USB/AT-command

**Status:** Accepted (dokument oceny — bez implementacji kodu)
**Data:** 2026-07-06

## Kontekst

`50-Quality/55-Roadmap.md` §9 (Faza 7): „Ocena wariantu Adaptera GSM opartego o modem USB/AT-command (Raspberry Pi, Linux) jako alternatywy dla Android SmsManager." Słowo „Ocena" różni się celowo od mocniejszego języka użytego dla pozostałych dwóch punktów Fazy 7 („Adapter WebSocket", „Uruchomienie Core") — ten ADR jest wyłącznie analizą wykonalności, **nie zawiera i nie wymaga żadnej implementacji kodu**.

Brak w projekcie jakiejkolwiek istniejącej dokumentacji technicznej AT-command/modemu USB (sprawdzone: wszystkie ADR, SPEC, dokumenty `20-Adapters`) poza tą jedną wzmianką w Roadmapie. Brak potwierdzonego dostępu do fizycznego modemu USB kompatybilnego z AT-command (w przeciwieństwie do Redmi Note 4 użytego jako rzeczywiste urządzenie testowe w Fazach 3-4).

## Analiza wykonalności

### 1. Dostęp do portu szeregowego z JVM/Linux

Standardowy JDK nie ma wbudowanego API do portów szeregowych (usunięte z Java od dawna, `javax.comm` nigdy nie było częścią standardowej biblioteki). Kandydat: **`jSerialComm`** (`com.fazecast:jSerialComm`) — biblioteka czysto Java, licencja Apache 2.0/GPLv2+ (dual), bez zewnętrznej kompilacji natywnej wymaganej od konsumenta (biblioteki natywne dołączone w JAR-ze), działa na Linux/Windows/macOS, powszechnie używana dokładnie do tego zastosowania (komunikacja AT-command z modemami GSM/LTE). Wymagałaby **czwartego świadomego wyjątku od minimalizacji zależności** (po jakarta.mail, kotlinx.serialization, kaml), jeśli implementacja zostanie kiedykolwiek podjęta.

### 2. Reużywalność istniejącej logiki `:adapter-gsm`

`:adapter-gsm` (`platform-android/build.gradle.kts`: plugin `com.android.library`) jest fundamentalnie modułem Android — **cały moduł kompiluje się względem Android SDK**, nawet jeśli poszczególne pliki nie importują `android.*` bezpośrednio. Sprawdzono 12 plików źródłowych:

| Kategoria | Pliki BEZ importu `android.*` (przenośna logika) | Pliki Z importem `android.*` (specyficzne dla Androida) |
|---|---|---|
| Liczba | 10 z 12 | 2 z 12 (`GsmAdapterFactory.kt`, `SmsSender.kt`) |
| Przykłady | `SmsPduConcatenator.kt` (26 linii — łączenie wieloczęściowych PDU), `SmsMessageMapper.kt`, `MmsMessageMapper.kt` (75 linii), `MmsNotificationParser.kt`, `MmsRetrieveConfParser.kt` (235 linii — parser WAP Push/MMS), `MmsSendRequestEncoder.kt`, `GsmRuntime.kt` (importuje wyłącznie `midomail.domain.gateway.GatewayInbound`) | Konstrukcja adaptera (`AdapterFactory`) i faktyczna wysyłka przez `SmsManager` |

**Wniosek:** znacząca część logiki protokołu (dekodowanie PDU, mapowanie SMS/MMS na `GatewayMessage`, parsowanie WAP Push/MMS retrieve-conf, enkodowanie żądań wysyłki MMS) jest już, przypadkowo, wolna od zależności Android na poziomie pojedynczych plików. Reużycie wymagałoby jednak **wydzielenia tych plików do nowego, wspólnego modułu czysto-Kotlinowego** (np. `:adapter-gsm-common`), co jest samo w sobie nietrywialną refaktoryzacją istniejącego, zamrożonego modułu Fazy 3 — nie jest to praca „za darmo".

Warstwa specyficzna dla AT-command (nawiązanie sesji z modemem, wysyłanie komend `AT+CMGS`/`AT+CMGL`/itd., parsowanie odpowiedzi modemu) musiałaby być napisana **od zera** — Android `SmsManager`/`SmsSender.kt` nie ma żadnego odpowiednika wielokrotnego użytku dla trybu AT-command.

### 3. Szacowany nakład pracy (gdyby podjęto pełną implementację)

- Wydzielenie wspólnej logiki PDU/MMS do nowego modułu: średni nakład, ryzyko regresji w `:adapter-gsm` (Faza 3, zamrożony Public API).
- Nowa warstwa AT-command (sesja modemu, komendy, parsowanie odpowiedzi, obsługa błędów/braku zasięgu — analogicznie do ADR-0009-Obciecie-Tresci-SMS.md dla Androida): duży nakład, wymaga rzeczywistego sprzętu do weryfikacji (różne modemy USB mają różne dialekty AT-command).
- Testy: bez fizycznego modemu USB weryfikacja ograniczona do testów jednostkowych parsera komend (bez potwierdzenia end-to-end na rzeczywistym sprzęcie) — nie spełnia dotychczasowego standardu projektu („weryfikacja na rzeczywistym urządzeniu" dla każdej fazy dotykającej sprzętu, Faza 3-4/Roadmapa §10).

### 4. Ograniczenia

- Brak potwierdzonego dostępu do fizycznego modemu USB/AT-command lub Raspberry Pi jako środowiska docelowego.
- Uprawnienia dostępu do portu szeregowego na Linuksie (`/dev/ttyUSB*`) wymagają zwykle członkostwa w grupie `dialout` lub uprawnień `root` — kwestia operacyjna, nie architektoniczna, ale wpływa na łatwość weryfikacji.

## Decyzja

**Wyłącznie ocena architektoniczna — bez implementacji kodu w tej fazie.** Rekomendacja: budowa pełnej implementacji adaptera GSM-modem odłożona do przyszłej fazy, uwarunkowana potwierdzonym dostępem do fizycznego sprzętu (modem USB kompatybilny z AT-command, najlepiej na Raspberry Pi zgodnie z Roadmapą) — bez tego, weryfikacja end-to-end nie spełniłaby standardu rygoru ustalonego w poprzednich fazach.

Jeśli implementacja zostanie kiedykolwiek podjęta:
1. Wydzielić przenośną logikę PDU/MMS z `:adapter-gsm` do nowego modułu współdzielonego (wymaga własnego ADR — zmiana istniejącego, zamrożonego modułu Fazy 3).
2. Przyjąć `jSerialComm` jako czwarty świadomy wyjątek od minimalizacji zależności (wymaga własnego ADR, analogicznie do ADR-0024/ADR-0032).
3. Zbudować nowy moduł `:adapter-gsm-modem` implementujący `Adapter`/`AdapterFactory` (SPEC-0006/SPEC-0010) — zero zmian w Gateway Engine/Routing Engine, zgodnie z kryterium wyjścia Fazy 7.

## Konsekwencje

- Zero nowego kodu, zero nowych zależności w tej fazie.
- Roadmapa §9 punkt 3 („Ocena wariantu...") uznany za spełniony przez ten dokument — dosłowne brzmienie wymaga oceny, nie budowy.
- Odnotowane w Architectural Debt Report Fazy 7 (Iteracja 7.9) jako świadomie odłożone.

## Dokumenty powiązane

- 20-Adapters/21-Adapter-GSM.md
- 90-ADR/ADR-0009-Obciecie-Tresci-SMS.md
- 91-Specification/SPEC-0006-Adapter-Contract.md, SPEC-0010-Plugin-SDK-Contract.md
- 50-Quality/55-Roadmap.md §9
