# ADR-0039 — Wątkowanie SMS↔e-mail po numerze telefonu

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

Nadzorca projektu zgłosił (zrzut ekranu Gmail Android): pełna rozmowa SMS↔e-mail nie zostaje w jednym wątku e-mailowym. Konkretny scenariusz:

1. Nadawca pisze SMS #1 → nadzorca otrzymuje e-mail #1 (wątek A).
2. Nadzorca odpowiada na e-mail #1 → nadawca poprawnie otrzymuje SMS z odpowiedzią.
3. Nadawca odpowiada na TEN SMS (SMS #2) → zamiast trafić do wątku A, przychodzi jako **całkowicie nowy, osobny wątek e-mail**, mimo identycznego tematu (numer telefonu).

Zgłoszenie wprost: „nadawca ma nawet nie wiedzieć, że wiadomość nie pochodzi z telefonu, ma czuć się tak jakby mu odpowiedziano przez telefon".

### Przyczyna

Krok 2 działa poprawnie, bo e-mail ma natywne wątkowanie RFC 5322 (`Message-ID`/`In-Reply-To`/`References`) — `EmailMessageMapper.fromMime()` już to obsługuje (`findParent()`/`threadingFrom()`, Iteracja 3.13). Krok 3 zawodzi, bo **SMS jako protokół nie niesie żadnego odpowiednika tych nagłówków** — `SmsMessageMapper.fromSms()` (do tej zmiany) zawsze mintował świeży, losowy `CorrelationId` dla KAŻDEGO przychodzącego SMS-a, bez wyjątku (świadoma decyzja udokumentowana wprost w kodzie od Iteracji 3.x — „każda przychodząca wiadomość jest korzeniem nowej nici"). Sam zgodny temat e-maila (numer telefonu) nie wystarcza — Gmail potwierdzone empirycznie NIE grupuje wątków wyłącznie po zgodności tematu bez poprawnych nagłówków `In-Reply-To`/`References`.

## Decyzja

**Numer telefonu jako trwały identyfikator rozmowy** — dokładnie tak, jak działa natywna aplikacja SMS na telefonie (jeden numer = jedna, ciągła rozmowa, bez pojęcia „zamknięcia wątku"). Gdy przychodzi nowy SMS, sprawdzamy: czy istnieje już jakakolwiek wcześniejsza wiadomość z/do tego numeru? Jeśli tak — dziedziczymy jej `CorrelationId` (kontynuacja tej samej rozmowy) zamiast zaczynać nową.

### Nowa zdolność `MessageStore`: `MessageQueryFilter.channelAddress`

`SPEC-0009-Message-Store-Contract.md` — nowe, opcjonalne pole filtra (`null` domyślnie, amendment addytywny): dopasowuje `Channel.address` źródła LUB celu. Konieczne, bo dotychczasowy `MessageStore` nie miał żadnego sposobu wyszukania wiadomości po adresie/numerze — wyłącznie po `MessageId`/`ExternalReference`/`CorrelationId`. Jedyna implementacja (`InMemoryMessageStore`) rozszerzona o dopasowanie tego pola; `query()` z tym filtrem, sortowaniem `CREATED_AT DESCENDING` i `PageRequest(size=1)` daje „najnowszą wiadomość dla tego numeru".

### `SmsMessageMapper`: opcjonalny `MessageStore`

Nowy, opcjonalny parametr konstruktora (`null` domyślnie — zachowuje dokładne poprzednie zachowanie dla wywołań bez niego). Gdy podany: `fromSms()` szuka najnowszej wiadomości dla numeru nadawcy przez `channelAddress`; jeśli znaleziona — dziedziczy jej `CorrelationId`, `causationId` = jej `MessageId`; jeśli nie (pierwszy SMS od tego numeru w historii) — zachowanie bez zmian (nowa nić). `GsmAdapterFactory` przekazuje `ports.messageStore` (już dostępny w tym punkcie kompozycji, ten sam wzorzec DI co wszędzie indziej).

### `EmailMessageMapper.toMime()`: przeniesienie korelacji na nagłówki RFC 5322

Sam fakt odziedziczenia `CorrelationId` przez SMS nie wystarcza — żeby Gmail POPRAWNIE zgrupował kolejny e-mail, trzeba ustawić `In-Reply-To`/`References` na PRAWDZIWY `Message-ID` (nie skrót SHA-256, którym SMS oznacza swoje wiadomości). Nowy, opcjonalny parametr `messageStore: MessageStore? = null` na `toMime()`: gdy podany, szuka w historii tej korelacji (`findByCorrelationId`) najnowszej wiadomości, która NAPRAWDĘ przyszła przez e-mail (`source.type == email` — taka wiadomość ma jako `ExternalReference` prawdziwy `Message-ID` nadany przez klienta pocztowy, wyekstrahowany bezpośrednio w `fromMime()`), i ustawia jej `ExternalReference` jako `In-Reply-To`+`References` na nowo wysyłanym e-mailu. `EmailAdapter.send()` przekazuje `ports.messageStore`.

**Świadome uproszczenie:** `References` ustawiane na POJEDYNCZY, najnowszy znany e-mail w wątku, nie na pełny, akumulowany łańcuch wszystkich poprzedników (ściśle wg RFC 5322 `References` powinno rosnąć o każdego przodka). Wystarczające dla Gmaila (potwierdzony klient w użyciu) — Gmail uzupełnia resztę łańcucha z WŁASNEJ kopii referencjonowanej wiadomości. Inny klient pocztowy z bardziej rygorystycznym wątkowaniem mógłby wymagać pełnego łańcucha — nieodkryty dziś problem, do rewizji gdyby się pojawił.

## Konsekwencje

- Amendment `:domain` (`MessageQueryFilter`) — pierwsza zmiana tej sesji dotykająca warstwy portów, nie tylko adapterów/platform. W pełni addytywny (nowe pole, wartość domyślna `null`), zero zmian w istniejących sygnaturach.
- `SmsMessageMapper`/`EmailMessageMapper.toMime()` — oba amendmenty addytywne (nowe opcjonalne parametry), wszystkie dotychczasowe wywołania (testy, `GmailHarness`) działają bez zmian.
- **Polityka „jeden numer = jedna rozmowa, na zawsze"** — bez okna czasowego/wygasania. Zgodne z zachowaniem natywnej aplikacji SMS, którą nadzorca projektu przywołał jako wzorzec. Jeśli w przyszłości pojawi się potrzeba „zamykania"/rozpoczynania nowego wątku dla tego samego numeru (np. zupełnie inny temat rozmowy po długiej przerwie) — wymaga osobnej decyzji, dziś świadomie nieobsłużone.
- Rozpoznawanie „czy to prawdziwy e-mail" opiera się na `source.type == email`, nie na formacie `ExternalReference` — prostsze, ale zakłada że TYLKO wiadomości odebrane przez `EmailMessageMapper.fromMime()` mają `source.type == email` z prawdziwym Message-ID (prawdziwe dziś, bo to jedyna droga tworzenia takich wiadomości).

## Weryfikacja

- Nowe testy: `InMemoryMessageStoreTest` (`channelAddress` filtruje po źródle/celu), `SmsMessageMapperTest` (dziedziczenie korelacji dla tego samego numeru; brak dziedziczenia dla innego numeru; pierwsza wiadomość nadal korzeniem; zachowanie niezmienione bez `MessageStore`), `EmailMessageMapperTest` (`In-Reply-To`/`References` ustawione gdy istnieje wcześniejszy e-mail w wątku; brak nagłówków bez `MessageStore` lub bez wcześniejszego e-maila).
- Pełna regresja (`:domain` przez `:platform-android`) — BUILD SUCCESSFUL, lint bez nowych ostrzeżeń.
- Zainstalowano na rzeczywistym urządzeniu testowym. **Pełne potwierdzenie end-to-end (rzeczywista, wieloetapowa wymiana SMS↔e-mail pozostająca w jednym wątku Gmail) wymaga testu na żywo przez nadzorcę projektu** — poza możliwościami tego środowiska.

## Dokumenty powiązane

- 91-Specification/SPEC-0009-Message-Store-Contract.md (nowe pole filtra `channelAddress`)
- 20-Adapters/21-Adapter-GSM.md, 20-Adapters/22-Adapter-Email.md
- Iteracja 3.13 (pełny łańcuch SMS → Email → odpowiedź → SMS — mechanizm, na którym buduje ta zmiana)
