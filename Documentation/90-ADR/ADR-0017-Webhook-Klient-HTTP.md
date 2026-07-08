# ADR-0017 — Webhook: wybór klienta HTTP

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

ADR-0007-Dostarczanie-Powiadomien.md rozstrzygnął, że Webhook to „generyczne wywołanie HTTP POST", ale nie wybrał konkretnego mechanizmu wykonania żądania. Żaden dokument (40-Platforms/40-Android.md, 41-JVM.md, 43-Przenosnosc.md) nie precyzuje klienta HTTP. Projekt konsekwentnie minimalizuje zależności zewnętrzne (50-Quality/51-Standard-kodowania.md) — jakarta.mail w Fazie 2 był jedynym dotąd świadomie zaakceptowanym wyjątkiem, uzasadnionym brakiem sensownej alternatywy wbudowanej dla SMTP/IMAP.

Rozważane opcje:

1. `java.net.http.HttpClient` (JDK 11+, brak nowej zależności Gradle) — nowoczesne API, ale na Androidzie dostępne dopiero od **API 34**. Urządzenie testowe zweryfikowane w Fazie 3 (Redmi Note 4, LineageOS) działa na **API 28** — użycie tego klienta crashowałoby dokładnie na sprzęcie, na którym cała Faza 3 była weryfikowana.
2. `java.net.HttpURLConnection` — dostępne od Android API 1 i w każdym JDK, zero nowych zależności, przenośne między `:notification-webhook` (JVM) a ewentualnym użyciem na Androidzie. Mniej wygodne API (strumienie, ręczne ustawianie nagłówków), ale w pełni wystarczające dla prostego POST z ciałem JSON i odczytu kodu odpowiedzi.
3. Biblioteka klienta (np. OkHttp) — odrzucone: sprzeczne z uzasadnieniem ADR-0007 („brak dodatkowych zależności/bibliotek klienckich... jeden generyczny mechanizm HTTP POST").

## Decyzja

`WebhookNotificationChannel` używa **`java.net.HttpURLConnection`** — dostępny bez dodatkowej zależności na obu docelowych platformach (JVM i Android API 28+), zgodny z zasadą minimalizacji zależności.

Ładunek JSON budowany jest ręcznie (proste, płaskie pola skalarne z `Alert` — poziom, źródło, czas, treść, status; 38-Powiadomienia.md §3) — w repozytorium nie istnieje dotąd żadna biblioteka JSON, więc wprowadzenie jej wyłącznie dla kilku pól byłoby nieproporcjonalne. Rozszerzenie o bibliotekę JSON, jeśli w przyszłości ładunek się skomplikuje, wymagałoby osobnego ADR.

Nowy moduł `:notification-webhook` (nie `:adapter-webhook` — ADR-0016 rozstrzygnął, że kanał powiadomień nie jest `Adapter`-em, nazwa modułu nie powinna sugerować inaczej) hostuje tę implementację, zależny wyłącznie od `:domain` — analogicznie do tego, jak `:adapter-email` trzyma jakarta.mail z dala od `:domain`.

## Konsekwencje

- Zero nowych zależności Gradle.
- API `HttpURLConnection` jest bardziej rozwlekłe niż `HttpClient`, ale to jednorazowy koszt w jednym, małym pliku.
- Jeśli w przyszłości minimalny wspierany poziom API Android wzrośnie powyżej 34, `java.net.http.HttpClient` stanie się realną alternatywą — nie blokujemy tej możliwości, tylko odkładamy ją do czasu, aż przestanie być ryzykiem dla realnego urządzenia testowego.

## Dokumenty powiązane

- 40-Platforms/40-Android.md
- 50-Quality/51-Standard-kodowania.md
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
- 90-ADR/ADR-0016-Notification-Channel-Port.md
- 91-Specification/SPEC-0019-Notification-Channel-Contract.md
