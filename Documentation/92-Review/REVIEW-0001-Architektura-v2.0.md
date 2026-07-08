# REVIEW-0001 — Przegląd architektury v2.0

**Status:** Zamknięty — dokumentacja zaakceptowana do zamrożenia
**Data zamknięcia:** 2026-07-05
**Autor:** Ci

## Wynik przeglądu

Architektura midoMAIL 2.0 jest gotowa do zamrożenia. Nie rekomenduje się dalszych zmian projektowych na tym etapie. Rekomendacja: formalna akceptacja dokumentacji (wykonana — patrz §Status dokumentów), a następnie rozpoczęcie implementacji zgodnie z 50-Quality/55-Roadmap.md, Faza 1.

## Przebieg przeglądu (rundy)

1. **Analiza wstępna (48 dokumentów)** — potwierdzono spójność Foundation/Core/Adapters/Infrastructure/Platforms z wcześniejszymi wnioskami z wersji 1.x; zidentyfikowano 3 luki krytyczne (ExternalReference, Android/domyślna apka SMS, semantyka Gmail IMAP) i szereg ważnych uzupełnień — patrz §Krytyczne poprawki i §Ważne uzupełnienia poniżej.
2. **Warstwa User Interface (12 dokumentów)** — zidentyfikowano i rozstrzygnięto fundamentalną niejednoznaczność „czym jest UI w Ports & Adapters" (rozstrzygnięte: ADR-0002, UI jako klient Adaptera REST/CLI), rozjazdy Dashboard/Processing State, model reguł routingu, multi-SIM, zakres RBAC, pojęcia Alert/Powiadomienie.
3. **Spójność po edycjach równoległych** — wykryto i naprawiono: zduplikowany słownik pojęć, kolizję numeracji, Roadmap naruszający zasadę Core First, brakujące sekcje „Dokumenty powiązane" w 9 plikach.
4. **Message Store (SPEC-0009)** — domknięto największą lukę: query API, schemat/indeksy, retention policy (w tym rozdzielenie retencji treści od retencji rekordu deduplikacji Exactly Once), performance targets, atomowość (`insertIfAbsent`).
5. **Plugin SDK (SPEC-0010)** — konkretny interfejs `Adapter`/`Capability`/`AdapterFactory`/`AdapterPorts` w pseudokodzie Kotlin, przepływ rejestracji i konfiguracji, mechanizm DI (konstruktor, bez frameworków) zastosowany wprost do adapterów.
6. **Schemat konfiguracji (SPEC-0005, ADR-0004)** — format YAML, pełny przykładowy plik, tabela typów/domyślnych/zakresów, walidacja krzyżowa.
7. **MessagePriority (ADR-0005)** — dodano priorytet komunikatu do modelu domenowego przed implementacją (uniknięcie breaking change), z jawnym rozróżnieniem od `Priority` reguły routingu.
8. **Rate Limiting (ADR-0006, SPEC-0011) i Powiadomienia (ADR-0007, 30-Infrastructure/38-Powiadomienia.md)** — architektura ograniczania przepustowości (per adapter/operacja, backpressure przez Scheduler, współpraca z Exactly Once) oraz dostarczania alertów (Email/Push/Webhook, integracja z PagerDuty/OpsGenie przez generyczny Webhook, eskalacja).
9. **Domknięcie końcowe** — End-User/Contact jako świadomy brak (00-Foundation/03-Model-domenowy.md, §7), backup i odtwarzanie Message Store z ryzykiem Exactly Once (30-Infrastructure/32-Baza-danych.md, §6), Multi-tenancy „nie teraz" (ADR-0008), offline behavior Adaptera GSM (21-Adapter-GSM.md, §9), obcięcie treści SMS (ADR-0009), szkielet dokumentacji użytkownika (50-Quality/54-Dokumentacja-uzytkownika.md, §4).

## Krytyczne poprawki przed zamrożeniem dokumentacji (domknięte)

1. Rozszerzyć GatewayMessage i Exactly Once o ExternalReference/SourceEventId jako naturalny klucz idempotencji dostarczany przez adapter.
2. Uzupełnić dokumentację Android o: status domyślnej aplikacji SMS/MMS (ADR-0003), PendingIntent SENT/DELIVERED, multipart SMS, Foreground Service/Doze oraz runtime permissions.
3. Uzupełnić Adapter Email o: IMAPS vs STARTTLS, semantykę Gmail READ_ONLY i \Seen, RFC 5322 Message-ID/In-Reply-To/References.

## Ważne uzupełnienia (domknięte)

- Polityka przechowywania sekretów.
- Strategia testów bez Robolectric i z rzeczywistymi usługami.
- Zasada konstruktorowego DI bez frameworków.
- Rozszerzenie Processing State o stany przekazania i potwierdzenia dostarczenia.
- Uzupełnienie Roadmap (8 faz, kryteria wyjścia, zgodność z Core First).

## Status dokumentów

Wszystkie dokumenty 00-Foundation, 10-Core, 20-Adapters, 30-Infrastructure, 40-Platforms, 50-Quality, 60-User-Interface oraz 91-Specification zmieniły status z „Projekt roboczy"/„Draft" na „Zaakceptowany"/„Accepted". Wszystkie 9 ADR (90-ADR) mają status „Accepted" od momentu utworzenia.

## Otwarte, świadomie odłożone decyzje

- Multi-tenancy — nie w zakresie wersji 2.0 (ADR-0008-Multi-Tenancy.md).
- Konkretne sygnatury operacji dla portów innych niż Message Store — SPEC-0002-Porty.md, §Plan dalszej specyfikacji (dopuszczalne odłożenie do czasu implementacji danego portu).

## Rekomendacja końcowa

Przejść do implementacji zgodnie z 50-Quality/55-Roadmap.md, poczynając od Fazy 1 (Core — bez adapterów, bez platformy, bez UI).

## Dokumenty powiązane

- 00-Foundation/03-Model-domenowy.md
- 50-Quality/55-Roadmap.md
- 90-ADR/ADR-0001-Communication-Gateway.md
- 90-ADR/ADR-0002-UI-jako-klient-adaptera.md
- 90-ADR/ADR-0003-Domyslna-aplikacja-SMS-MMS.md
- 90-ADR/ADR-0004-Format-Konfiguracji.md
- 90-ADR/ADR-0005-Message-Priority.md
- 90-ADR/ADR-0006-Rate-Limiting.md
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
- 90-ADR/ADR-0008-Multi-Tenancy.md
- 90-ADR/ADR-0009-Obciecie-Tresci-SMS.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
