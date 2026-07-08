<p align="center">
  <img src="midoMAIL-branding/logo/logo_transparent_256.png" width="128" alt="midoMAIL logo">
</p>

# midoMAIL 2.0

**Communication Gateway** — autonomiczny silnik komunikacyjny do bezpiecznego,
niezawodnego i kontrolowanego przekazywania komunikatów pomiędzy różnymi
kanałami (SMS/MMS, e-mail, REST, WebSocket, CLI). Niezależny od transportu,
protokołu i platformy uruchomieniowej.

midoMAIL **jest**: bramą integracyjną, silnikiem routingu komunikatów,
fundamentem do budowy własnych adapterów.
midoMAIL **nie jest**: aplikacją SMS, klientem poczty, komunikatorem ani
produktem zależnym od Androida — Android to jedna z kilku wspieranych platform
uruchomieniowych, obok czystego JVM/Linux.

## Architektura

Projekt wielomodułowy (Kotlin, Gradle), zorganizowany wokół zasady
"Core First" — rdzeń domenowy nie zależy od żadnego adaptera ani platformy:

- **`:domain`** — model domenowy, Gateway Engine, Routing Engine, porty (bez implementacji platformowych)
- **`:adapter-email`**, **`:adapter-gsm`**, **`:adapter-rest`**, **`:adapter-cli`**, **`:adapter-websocket`** — adaptery transportowe, każdy komunikuje się z rdzeniem wyłącznie przez porty
- **`:platform-jvm`** — kompozycja uruchomieniowa na czystym JVM/Linux (Email+WebSocket+REST+CLI+UI w jednym procesie)
- **`:platform-android`** — kompozycja uruchomieniowa na Androidzie (SMS/MMS jako domyślna aplikacja systemowa)
- **`:ui-web`** — panel administracyjny (statyczny frontend, klient Adaptera REST/CLI)
- **`:notification-webhook`** — kanał powiadomień (integracja PagerDuty/OpsGenie/Slack przez webhook)

Pełna dokumentacja architektoniczna (wizja, model domenowy, kontrakty, ADR-y,
roadmapa) znajduje się w [`Documentation/`](Documentation/README.md).

## Budowanie

Wymagany jest wyłącznie **JDK 17+** — reszta (Gradle, zależności) pobiera się
automatycznie przez dołączony wrapper.

```bash
./release.sh
```

Skrypt sam sprawdza środowisko, buduje przenośną dystrybucję Gateway
(`:platform-jvm`) i — jeśli wykryje zainstalowany Android SDK — również
podpisany APK (`:platform-android`), generując przy pierwszym uruchomieniu
własny klucz podpisywania (patrz [`KEYSTORE.md`](KEYSTORE.md)). Wynik trafia
do `build/output/midoMAIL-vX.Y-buildNNNNN.zip` razem z sumami kontrolnymi
SHA-256 i instrukcją uruchomienia.

Bez Android SDK skrypt buduje samą dystrybucję JVM — Gateway w pełni działa
bez Androida.

## Uruchomienie (Gateway JVM)

```bash
./gateway-jvm/bin/platform-jvm
```

Domyślne porty: `8080` (Admin REST API), `8081` (UI). Przy pierwszym
uruchomieniu proces tworzy lokalną konfigurację i sekrety oraz wypisuje w
konsoli losowy klucz Admin API.

## Aplikacja Android

⚠️ **To nie jest zwykła aplikacja SMS do codziennego użytku.** midoMAIL na
Androidzie to komponent bramy komunikacyjnej — po instalacji **przejmuje rolę
domyślnej aplikacji SMS/MMS i zastępuje standardową appkę Wiadomości**
systemu (wymóg platformy, nie opcja — bez tej roli Android nie gwarantuje
odbioru MMS). Nie ma tradycyjnego interfejsu konwersacji jak w typowym
komunikatorze — komunikaty widać przez panel administracyjny (`:ui-web`) albo
REST/CLI Gateway'a. Z tego powodu lepiej sprawdza się na dedykowanym/zapasowym
telefonie z aktywną kartą SIM niż na głównym telefonie do codziennej obsługi
wiadomości.

**Wymagania:**
- Android 9.0 (API 28) lub nowszy (`minSdk 28`)
- Aktywna karta SIM z zasięgiem (SMS/MMS wymagają realnej sieci komórkowej)
- Zgoda na rolę domyślnej aplikacji SMS/MMS przy pierwszym uruchomieniu

**Testowane na:** Xiaomi Redmi Note 4 (codename `mido`), Android 9. Inne
urządzenia/wersje Androida powinny działać zgodnie ze specyfikacją, ale nie
były jeszcze realnie zweryfikowane — informacje o zgodności (lub jej braku)
mile widziane przez Issues.

## Wesprzyj projekt

Jeśli midoMAIL okazał się przydatny, gwiazdka na GitHubie albo wsparcie przez
[GitHub Sponsors](https://github.com/sponsors/Irek-Mc) bardzo się przydają i
motywują do dalszej pracy nad projektem.

## Licencja

[MIT](LICENSE)
