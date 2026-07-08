# ADR-0003 — midoMAIL jako domyślna aplikacja SMS/MMS

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

Wersja 1.x działała jako aplikacja SMS niebędąca domyślną obsługą wiadomości na urządzeniu. Diagnostyka wersji 1.x (Zadania 028–030) wykazała twarde ograniczenia tego stanu: Android nie uzupełnia pól `date_sent`/`protocol`/`service_center` w rekordzie SMS dla niedomyślnej aplikacji, systemowa apka Wiadomości pokazuje mylący status „Nie wysłano" mimo poprawnej wysyłki, a odbiór MMS wymaga uprawnień (`WAP_PUSH_RECEIVED`) i zachowań systemowych zarezerwowanych w praktyce dla domyślnej aplikacji SMS/MMS — bez tej roli midoMAIL może w ogóle nie otrzymać powiadomienia o przychodzącym MMS.

Produkt ma pełnić rolę autonomicznego serwera komunikacyjnego („serwerek"), a nie dodatkowej aplikacji obok systemowej apki Wiadomości — te dwie role się wykluczają na poziomie systemu Android (tylko jedna aplikacja może być domyślną obsługą SMS/MMS jednocześnie).

## Decyzja

midoMAIL **zakłada z góry rolę domyślnej aplikacji SMS/MMS** na platformie Android. Nie jest to opcja rozważana warunkowo w trakcie implementacji — jest to wymóg architektoniczny obowiązujący od Fazy 3 Roadmapy (55-Roadmap.md).

midoMAIL **zastępuje** systemową obsługę SMS/MMS na urządzeniu, a nie uzupełnia jej.

## Konsekwencje

- Adapter GSM musi implementować pełny zestaw komponentów wymaganych przez Android dla roli domyślnej aplikacji SMS: odbiornik `SMS_DELIVER_ACTION` (zamiast `SMS_RECEIVED_ACTION`), odbiornik `WAP_PUSH_DELIVER_ACTION` dla MMS, aktywność obsługującą `ACTION_SENDTO`, obsługę „Quick Response"/`RESPOND_VIA_MESSAGE_ACTION` — nawet jeśli midoMAIL nie prezentuje tradycyjnego UI konwersacji (40-Platforms/40-Android.md).
- Adapter GSM obsługuje MMS jako pełnoprawny, odrębny transport obok SMS — nie jako rozszerzenie SMS (20-Adapters/21-Adapter-GSM.md).
- Użytkownik traci dostęp do systemowej apki Wiadomości jako głównego interfejsu SMS/MMS na tym urządzeniu — midoMAIL ją zastępuje. Jest to świadoma, zaakceptowana konsekwencja zgodna z wizją produktu (00-Foundation/01-Wizja-produktu.md: „midoMAIL nie jest aplikacją SMS" — czyli nie jest *dodatkiem* do niej, lecz jej zamiennikiem na poziomie platformy).
- Rejestracja jako domyślna aplikacja SMS wymaga zgody użytkownika udzielanej w czasie działania (rola `RoleManager.ROLE_SMS` na nowszych wersjach Androida, `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT` na starszych) — mechanizm żądania tej roli jest wymaganiem Fazy 3, nie elementem opcjonalnym.
- Odbiór MMS jako pełnoprawny kanał wejściowy do Gateway wymaga rozszerzenia modelu Payload o załączniki (SPEC-0001-GatewayMessage.md).

## Dokumenty powiązane

- 00-Foundation/01-Wizja-produktu.md
- 20-Adapters/21-Adapter-GSM.md
- 40-Platforms/40-Android.md
- 50-Quality/55-Roadmap.md
- 91-Specification/SPEC-0001-GatewayMessage.md
