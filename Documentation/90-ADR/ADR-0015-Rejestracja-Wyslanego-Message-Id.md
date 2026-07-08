# ADR-0015 — Rejestracja wysłanego Message-ID przez adapter

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`AdapterPorts.messageStore` jest udokumentowany jako przekazywany adapterowi wyłącznie do odczytu (10-Core/14-Registry-Adapterow.md, KDoc `AdapterPorts`; `EmailMessageMapper.fromMime` czyta go, żeby rozstrzygnąć wątkowanie na podstawie `In-Reply-To`/`References`). Żaden adapter nie zapisuje do niego bezpośrednio — jedynym miejscem zapisu jest `ExactlyOnceEngine` wewnątrz `GatewayEngine.receive()`, dla komunikatów PRZYCHODZĄCYCH.

Podczas ręcznej weryfikacji produkcyjnej Fazy 2 (docs/faza2-weryfikacja-gmail.md, scenariusz 3) i podczas budowy Iteracji 3.13 (pełny łańcuch SMS → Email → odpowiedź → SMS) ujawnił się realny brak: `EmailAdapter.send()` nigdy nie rejestruje wysłanej wiadomości pod jej rzeczywistym, przydzielonym przez serwer SMTP nagłówkiem `Message-ID`. Gdy odbiorca odpowiada, jego klient pocztowy ustawia `In-Reply-To` na ten właśnie identyfikator — `EmailMessageMapper.fromMime()` (odbiór odpowiedzi) próbuje znaleźć oryginał przez `MessageStore.findByExternalReference()`, ale nic go tam nie zapisało. Wątkowanie (CorrelationId/CausationId) nie działa dla żadnej wiadomości WYSŁANEJ przez adapter, tylko dla wiadomości odebranych, które same były odpowiedzią na coś wcześniej odebranego.

W Fazie 2 ten brak został jedynie obieżony w harnessu testowym (ręczny, jednorazowy zapis do `MessageStore` poza kodem produkcyjnym) — nigdy nie naprawiony w `EmailAdapter` samym. Iteracja 3.13 (łańcuch międzykanałowy, gdzie SMS przekazywany jest jako e-mail, a odpowiedź musi skorelować się z powrotem do oryginalnego SMS-a) czyni ten brak blokującym.

## Decyzja

Adapter **może** zapisać do `MessageStore` w jednym, wąskim, jawnie udokumentowanym celu: zarejestrować odwzorowanie „identyfikator przydzielony wysłanej wiadomości przez zewnętrzny system → oryginalny `GatewayMessage`", żeby przyszła odpowiedź mogła się do niego odwołać przez `findByExternalReference`.

Ograniczenia tego wyjątku:
- adapter zapisuje WYŁĄCZNIE bezpośrednio po udanej wysyłce, WYŁĄCZNIE komunikat, który sam właśnie wysłał,
- zapisywany jest DOKŁADNIE ten sam `GatewayMessage`, który adapter otrzymał do wysłania — bez modyfikacji `identity` (poza kluczem, pod którym trafia do magazynu — `insertIfAbsent` przyjmuje `ExternalReference` niezależny od `message.identity.externalReference`),
- adapter nigdy nie CZYTA z `MessageStore` w celu podjęcia decyzji o wysyłce (to pozostaje wyłącznie do odczytu, jak dotąd — czytanie służy tylko wątkowaniu PRZYCHODZĄCYCH odpowiedzi, niezmienione),
- brak zapisu (np. transport nie ujawnia identyfikatora) nie jest błędem — wątkowanie po prostu nie zadziała dla tej wiadomości, zgodnie z już ustalonym zachowaniem `fromMime` dla braku dopasowania.

## Konsekwencje

- `EmailAdapter.send()` odczytuje nagłówek `Message-ID` z `MimeMessage` PO wysyłce (przydzielany przez serwer SMTP, jeśli nie ustawiony jawnie) i wywołuje `ports.messageStore.insertIfAbsent(ExternalReference(messageId), message)`.
- Ten sam wzorzec dotyczy przyszłych adapterów, których transport ma analogiczny mechanizm identyfikacji wysłanej wiadomości (np. potencjalnie inne kanały z własnym ID wiadomości) — nie tylko Email.
- `AdapterPorts.messageStore` pozostaje w kontrakcie bez zmiany typu — to zmiana DOZWOLONEGO UŻYCIA portu przez adapter, nie zmiana sygnatury.

## Dokumenty powiązane

- 10-Core/14-Registry-Adapterow.md
- 20-Adapters/22-Adapter-Email.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
- docs/faza2-weryfikacja-gmail.md (scenariusz 3)
