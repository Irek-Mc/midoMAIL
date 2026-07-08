# ADR-0022 — Moduły Adapter REST/CLI i środowisko uruchomienia

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

Faza 5 potrzebuje miejsca na kod Adaptera REST i Adaptera CLI, oraz środowiska, w którym da się je faktycznie uruchomić i zweryfikować. Dotychczasowy wzorzec projektu: `:adapter-email`/`:adapter-gsm`/`:notification-webhook` — moduły zależne wyłącznie od `:domain`, z platformowo-specyficznym kodem (jakarta.mail, Android SDK, `HttpURLConnection`) trzymanym z dala od `:domain`.

40-Platforms/41-JVM.md §4 wymienia „narzędzia administracyjne" jako typowe zastosowanie JVM; 42-Linux.md wymienia serwery/CI-CD/edge. Żaden dokument nie mówi wprost „Adapter CLI działa wyłącznie na JVM/Linux, nigdy na Android", ale brak jakiejkolwiek interaktywnej powłoki na tle działającej usłudze Android czyni to praktycznie oczywistym. Roadmap §9 (Faza 7) explicité przypisuje sobie „Core na czystym JVM/Linux jako dowód przenośności" jako PRZYSZŁĄ pracę — budowanie pełnego `:platform-jvm` już teraz wyprzedzałoby udokumentowany harmonogram.

## Decyzja

Dwa nowe moduły Gradle, zależne wyłącznie od `:domain` (wzorem istniejących adapterów):

- `:adapter-rest` — `kotlin("jvm")`, serwer HTTP administracyjny (ADR-0023).
- `:adapter-cli` — `kotlin("jvm")`, dyspozytor komend administracyjnych (ADR-0025).

**Bez nowego `:platform-jvm` w tej fazie.** Zamiast tego: konwencja `manualVerification` (już użyta w `:adapter-email`, Faza 2) — osobny source set, nieuruchamiany automatycznie w `gradle test`/CI, jawnie nieprodukcyjny, służący wyłącznie do ręcznej weryfikacji end-to-end (Iteracja 5.17). To jest środowisko uruchomienia obu adapterów tej fazy — nie pełnoprawny, przenośny punkt kompozycji, którego dowód pozostaje Fazą 7.

## Konsekwencje

- Zero nowych zależności wprowadzonych samym szkieletem modułów.
- Adapter REST/CLI nie mają dziś ŻADNEGO stałego, produkcyjnego miejsca uruchomienia (Android nie jest właściwym gospodarzem, `:platform-jvm` jeszcze nie istnieje) — to świadomo odnotowany dług architektoniczny tej fazy (Iteracja 5.18), nie przeoczenie.
- Kiedy Faza 7 zbuduje `:platform-jvm`, `:adapter-rest`/`:adapter-cli` będą gotowe do wpięcia w tę kompozycję bez zmian (zależą wyłącznie od `:domain`, nie od żadnego istniejącego punktu kompozycji Android).

## Dokumenty powiązane

- 40-Platforms/41-JVM.md
- 40-Platforms/42-Linux.md
- 50-Quality/55-Roadmap.md, §9 (Faza 7)
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
