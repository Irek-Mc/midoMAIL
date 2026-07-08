# ADR-0036 — Moduł `:platform-jvm` jako punkt kompozycyjny

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`50-Quality/55-Roadmap.md` §9 wymaga uruchomienia Core na czystym JVM/Linux, bez Androida, jako dowodu przenośności (43-Przenosnosc.md). Ustalono w trakcie researchu (Iteracja 7.0):

- `:adapter-email` już działa bez zmian na czystym JVM od Fazy 2 (zależności czysto JVM-owe, własny harness na prawdziwym Gmailu).
- `:adapter-rest`/`:adapter-cli`/`:ui-web` NIGDY nie zostały złożone w jeden realny proces z prawdziwymi adapterami produkcyjnymi — dotąd wyłącznie oddzielne harnesse `manualVerification` z adapterami syntetycznymi.
- `:platform-android` (`GatewayForegroundService`) hardkoduje reguły routingu i NIE konstruuje `RoutingRuleAdministration`/`ConfigurationProvider` wcale.
- Faza 5's raport zawierał rekomendację: „Rozważyć `:platform-jvm` w Fazie 7 (...) jako miejsce na prawdziwy, jednoprocesowy test międzyadapterowy REST↔CLI."

Ten ADR rozstrzyga zakres i kształt nowego modułu `:platform-jvm` oraz punkty SA-18/SA-19 z SPEC-0026.

## Decyzja

**Nowy moduł `:platform-jvm`** (`kotlin("jvm")`, z pluginem `application` dla `main()` uruchamialnego przez `./gradlew :platform-jvm:run`) — **pełna kompozycja**: Email + WebSocket + REST + CLI + UI w jednym, realnym procesie, jednej pamięci. Zależy od `:domain`, `:adapter-email`, `:adapter-websocket`, `:adapter-rest`, `:adapter-cli`, `:ui-web`, `:notification-webhook`.

W przeciwieństwie do `:platform-android`, `:platform-jvm` konstruuje realnie `RoutingRuleAdministration` i `ConfigurationProvider` — Admin API (REST/CLI/UI) działa w pełni, nie jest martwym kodem obok zahardkodowanej konfiguracji.

### SA-18 — implementacje portów JVM

| Port | Implementacja `:platform-jvm` | Uzasadnienie |
|---|---|---|
| `ResourceMonitor` | `JvmResourceMonitor` (nowa, realna implementacja) — zastępuje `UnavailableResourceMonitor` (Faza 6, świadomy stub). CPU: `com.sun.management.OperatingSystemMXBean.getCpuLoad()` (rzutowanie z `ManagementFactory.getOperatingSystemMXBean()` — dostępne w standardowym JDK Oracle/OpenJDK, nie wymaga nowej zależności). RAM: `Runtime.getRuntime()` (heap) + `OperatingSystemMXBean.getTotalMemorySize()`/`getFreeMemorySize()` (RAM systemowy). Storage: `java.nio.file.FileStore` (`Files.getFileStore(path).getUsableSpace()`/`getTotalSpace()`). Network: pole `null` — brak przenośnego, zależnego wyłącznie od JDK API do zliczania bajtów sieciowych (odpowiednik `TrafficStats` z Androida nie istnieje w standardowym JDK) — udokumentowane ograniczenie, spójne z tym, że `ResourceSnapshot` ma już WSZYSTKIE pola nullable (ADR-0027). |
| `SecretStore` | `FileSecretStore` (nowa implementacja) — plik lokalny (np. `~/.midomail/secrets.properties`, uprawnienia `600` ustawiane jawnie po zapisie przez `Files.setPosixFilePermissions`), format klucz-wartość. Zgodne z `security.secretStore: "file"`, już przewidzianym w SPEC-0005 §5 obok `android-keystore`/`env`. Jawnie udokumentowane jako uproszczenie — nie prawdziwy keystore/vault (Architectural Debt Report, Iteracja 7.9). |
| `ConfigurationProvider` | `FileConfigurationProvider` (nowa implementacja, NIE `InMemoryConfigurationProvider`) — persystuje wartości (w tym pełny dokument YAML pod kluczem `gateway.fullConfiguration`, ADR-0032) do pliku na dysku, wczytywanego przy starcie procesu. Uzasadnienie: `:platform-jvm` to pierwszy realny, długo działający proces serwerowy w projekcie — `InMemoryConfigurationProvider` tracący całą konfigurację przy restarcie procesu byłby nieużyteczny operacyjnie. Domyka też otwartą rekomendację z raportu Fazy 5 („rozważyć trwałą historię konfiguracji"). |
| `SchedulerProvider` | **Reużycie `InMemorySchedulerProvider` z `:domain` wprost, bez zmian.** Odkrycie z Iteracji 6.29 (wątki nie-daemon bez `shutdown()`) dotyczyło KRÓTKOTRWAŁEGO harnessu weryfikacyjnego, który MIAŁ się zakończyć — dla `:platform-jvm`, będącego realnym, długo działającym procesem serwerowym, wątki nie-daemon utrzymujące proces przy życiu są POŻĄDANYM zachowaniem (serwer ma działać, dopóki nie zostanie jawnie zatrzymany), nie błędem. Brak zmiany w `:domain`. |

### SA-19 — źródło reguł routingu przy starcie

Plik YAML na dysku (ścieżka konfigurowalna, domyślnie `./config.yaml` w katalogu roboczym procesu), wczytywany przy starcie przez `YamlConfigurationCodec.decode()` (już zbudowany w `:adapter-rest`, Iteracja 6.7/6.14) → `ConfigurationDocument.routing.rules` → seeduje konstruktor `RoutingRuleAdministration`. Jeśli plik nie istnieje przy pierwszym starcie, proces startuje z pustą listą reguł (Admin API/UI pozwala dodać je później) — nie traktowane jako błąd fatalny.

### Amendment (odkryte podczas budowy Iteracji 7.7) — propagacja zmian reguł do żywego przetwarzania

Analiza `GatewayEngine.kt` ujawniła: `routingEngine` jest polem `private val` (niemutowalnym), a `RoutingEngine` to zamknięta klasa `final` (bez `open`) — **nie istnieje dziś ŻADEN mechanizm podmiany instancji `RoutingEngine` używanej przez już skonstruowany `GatewayEngine`**. W Fazach 5-6 nie miało to znaczenia: `:platform-android` w ogóle nie konstruuje `RoutingRuleAdministration` (reguły zahardkodowane), a harnesse `manualVerification` nie testowały propagacji zmian reguł administracyjnych do żywego `GatewayEngine.receive()` — wyłącznie do symulatora (`RoutingRuleAdministration.buildEngine()`, wywoływane na żądanie, zawsze świeże).

Dla `:platform-jvm` (pierwszy proces z REALNYM `GatewayEngine` obok REALNEJ `RoutingRuleAdministration`) ma to znaczenie: bez rozwiązania, zmiany reguł przez REST/CLI/UI byłyby widoczne w symulatorze i historii, ale **nie wpływałyby na faktyczne trasowanie żywych komunikatów** aż do restartu procesu.

**Rozwiązanie — `SwappableGatewayInbound`** (nowa, mała klasa kompozycyjna w `:platform-jvm`, analogiczna do `AdapterRegistryOutbound` z `:platform-android`): implementuje `GatewayInbound` (już istniejący interfejs — zero zmian), trzyma `@Volatile var delegate: GatewayInbound` wskazujący na AKTUALNY `GatewayEngine`, deleguje `receive()`. Przekazywana jako `AdapterPorts.gatewayInbound` do WSZYSTKICH adapterów zamiast bezpośrednio `GatewayEngine`. Zaplanowane zadanie (`SchedulerProvider`, np. co 5s) porównuje `routingRuleAdministration.list()` z regułami użytymi do zbudowania aktualnego `GatewayEngine`; przy różnicy — konstruuje nowy `GatewayEngine` (te same instancje `exactlyOnceEngine`/`eventPublisher`/`gatewayOutbound`, świeży `RoutingEngine(nowe reguły)`) i podmienia `delegate`.

**Zero zmian w `:domain`** — `GatewayEngine`/`RoutingEngine`/`GatewayInbound` całkowicie nietknięte, zgodnie z kryterium wyjścia Fazy 7 („identyczny model domenowy i Gateway Engine, bez zmian"). Koszt: propagacja zmian reguł do żywego przetwarzania ma opóźnienie rzędu interwału odpytywania (kilka sekund), nie natychmiastowa — udokumentowane jako świadomy kompromis (ten sam duch co SA-11 z Fazy 6, polling zamiast prawdziwego strumienia).

## Konsekwencje

- `:platform-jvm` zależy od `:adapter-rest` (dla `YamlConfigurationCodec`) mimo że sam nie wystawia REST-a jako OSOBNEGO modułu transportowego — to punkt kompozycyjny, nie nowy adapter; taka zależność jest zgodna z rolą platformy (30-Infrastructure, 40-Platforms) integrującej gotowe komponenty, nie z rolą `:domain` (które musi mieć zero zależności).
- Trzy nowe implementacje portów (`JvmResourceMonitor`, `FileSecretStore`, `FileConfigurationProvider`) — żadna nie zmienia istniejących interfejsów `:domain`, wszystkie to nowe klasy implementujące już zamrożone kontrakty.
- `SwappableGatewayInbound` (nowa klasa kompozycyjna, §Amendment powyżej) — jedyny sposób, w jaki zmiany reguł routingu przez Admin API faktycznie wpływają na żywe przetwarzanie komunikatów w `:platform-jvm`, bez modyfikacji `GatewayEngine`/`RoutingEngine`.
- CORS `*` (ADR-0033, Faza 6) pozostaje jawnie odnotowanym punktem do zawężenia w przyszłości — poza zakresem tego ADR, nie blokuje kryterium wyjścia Fazy 7 (nie jest wymienione w Roadmapie §9 dosłownie).
- Weryfikacja end-to-end (Iteracja 7.8) uruchamia proces na tej samej maszynie Linux (już potwierdzone jako wystarczające środowisko dowodu przenośności).

## Dokumenty powiązane

- 91-Specification/SPEC-0026-WebSocket-Adapter-Contract.md
- 90-ADR/ADR-0027-Resource-Monitor.md
- 90-ADR/ADR-0020-Konfiguracja-Zapis.md
- 90-ADR/ADR-0032-Konfiguracja-YAML-Pelna.md
- 40-Platforms/41-JVM.md, 40-Platforms/43-Przenosnosc.md
- docs/faza5-raport-weryfikacji.md §7 (rekomendacje)
