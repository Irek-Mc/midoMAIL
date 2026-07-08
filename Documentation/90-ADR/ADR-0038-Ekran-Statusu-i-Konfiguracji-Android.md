# ADR-0038 — Ekran statusu i konfiguracji `MainActivity` (Android)

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

Faza 7 (ostatnia zdefiniowana w `50-Quality/55-Roadmap.md`) jest zamknięta. Nadzorca projektu zgłosił dwa realne problemy z aplikacją na telefonie, poza zakresem Roadmapy:

1. Poświadczenia e-mail (i URL webhooka powiadomień) da się dziś ustawić WYŁĄCZNIE przez ręczny broadcast ADB (`DebugCredentialProvisioningReceiver`) — nie ma żadnego ekranu w aplikacji. Nadzorca utknął na testowych danych logowania bez sposobu, by je zmienić bez narzędzi deweloperskich.
2. `MainActivity` nie ma żadnego widocznego interfejsu (`onCreate()` nie wywołuje `setContentView()`) — świadoma decyzja Fazy 3 (ADR-0003-Domyslna-aplikacja-SMS-MMS.md: „midoMAIL nie prezentuje tradycyjnego UI konwersacji"), ale skutkująca pustym ekranem, który sprawia wrażenie niedokończonej aplikacji.

Pierwotna wizja nadzorcy: pełny panel administracyjny dostępny przez sieć z telefonu (adres IP:port, połączenie z laptopa). **Zweryfikowano i odrzucono w tej iteracji**: `com.sun.net.httpserver.HttpServer`, na którym zbudowany jest cały istniejący Adapter REST (`:adapter-rest`) i panel webowy (`:ui-web`), **nie istnieje w Android SDK** (potwierdzone: pakiet `com.sun.net.httpserver` całkowicie nieobecny w `android.jar`, sprawdzone `unzip -l` na `platforms/android-34/android.jar`). Zbudowanie pełnego panelu sieciowego na telefonie wymagałoby przyjęcia nowej biblioteki HTTP dla Androida (np. NanoHTTPD — kolejny świadomy wyjątek od minimalizacji zależności) i przebudowy warstwy transportowej Adaptera REST wokół niej — osobny, większy projekt, nie „doklejenie" istniejącego kodu.

## Decyzja

**Nowy, prawdziwy ekran w `MainActivity`** (Android Views + `res/layout/activity_main.xml`, bez Jetpack Compose — pierwszy i jedyny ekran aplikacji nie uzasadnia nowego paradygmatu UI), zastępujący pusty ekran. Zakres ekranu (ustalony w rozmowie z nadzorcą projektu):

1. Status usługi (`GatewayForegroundService` działa/zatrzymana).
2. Status „czy jestem domyślną aplikacją SMS" — reużycie istniejącej logiki `Telephony.Sms.getDefaultSmsPackage(this) == packageName` (już używanej wewnątrz `requestDefaultSmsAppRoleIfNeeded()`), wyłącznie odczyt.
3. Podstawowe liczniki/zdrowie zarejestrowanych adapterów (GSM, Email) — wysłane/odebrane, zdrowy/niezdrowy.
4. Formularz konfiguracji: host/port SMTP, host/port IMAP, login, hasło, adres przekierowania, URL webhooka powiadomień (opcjonalny).
5. Przycisk „Zapisz i uruchom ponownie".

### To NIE jest „UI" w rozumieniu ADR-0002-UI-jako-klient-adaptera.md

ADR-0002 reguluje `:ui-web` — komponent komunikujący się z Gateway WYŁĄCZNIE przez Adapter REST/CLI, nigdy bezpośrednio z portami Core. Ten ekran jest czymś innym: częścią TEGO SAMEGO procesu aplikacji co `GatewayForegroundService` (nie osobnym komponentem/modułem), czytającą stan bezpośrednio z żywych obiektów w pamięci (`Registry`, `Metrics`, `HealthStatus`) — analogicznie do tego, jak `:platform-jvm`'s `main()` drukuje baner statusu wprost z obiektów kompozycji, nie przez własne REST API. Rozróżnienie jest celowe i udokumentowane tutaj, żeby uniknąć przyszłego pytania „dlaczego ten ekran łamie ADR-0002" — nie łamie, bo nie jest jego przedmiotem.

### Mechanizm dostępu do stanu: statyczna referencja do instancji serwisu

`MainActivity` i `GatewayForegroundService` działają w tym samym procesie (brak `android:process` w `AndroidManifest.xml`). Zamiast `Bound Service`/`Binder` (idiomatyczny, ale cięższy wzorzec Android dla międzykomponentowej komunikacji), przyjęto prostszy: `GatewayForegroundService` wystawia `companion object { @Volatile var instance: GatewayForegroundService? = null }`, ustawiany w `onCreate()` i czyszczony w `onDestroy()`. `MainActivity` odczytuje `GatewayForegroundService.instance?.statusSnapshot()` bezpośrednio.

**Uzasadnienie świadomego uproszczenia:** jedynym konsumentem jest ten jeden ekran diagnostyczny w tej samej aplikacji — nie publiczny kontrakt międzyprocesowy, nie zewnętrzna integracja. Pełny `Bound Service` byłby nieproporcjonalnym nakładem względem wartości. Jeśli w przyszłości pojawi się drugi konsument (np. widget, komenda Tasker/Shortcuts) — należy wtedy ponownie ocenić tę decyzję, nie rozszerzać jej cicho.

### Odświeżanie ekranu: cykliczne (polling), nie reaktywne

`MainActivity` odświeża status/liczniki co ~3 sekundy (`Handler.postDelayed`), nie przez obserwowalny strumień (`Flow`/`LiveData`). Ten sam duch co polling w `js/views/logs.js` (Iteracja 6.21, SA-11, Faza 6) — świadome uproszczenie, nie w pełni reaktywny UI. Wystarczające dla ekranu diagnostycznego jednej osoby patrzącej na własny telefon, nie dla publicznego, wieloużytkownikowego panelu.

### Konfiguracja: rozszerzenie istniejącego mechanizmu, nie nowy

- Sekrety nadal przez `AndroidKeystoreSecretStore` (już istniejący, prawdziwe szyfrowanie AES-GCM w Android Keystore) — ekran to wyłącznie interfejs NAD istniejącym mechanizmem, nie nowy magazyn.
- Host/port SMTP/IMAP (dziś zahardkodowane na `smtp.gmail.com:587`/`imap.gmail.com:993` w `GatewayForegroundService.kt`) stają się konfigurowalne przez nowe klucze sekretów, z tymi samymi wartościami jako domyślne — identyczny wzorzec jak `createEmailAdapterIfConfigured` w `platform-jvm/src/main/kotlin/midomail/platform/jvm/Main.kt` (Faza 7, Iteracja 7.8), gdzie ten sam problem (elastyczność hosta SMTP/IMAP) już wystąpił i został rozwiązany identycznie.
- `DebugCredentialProvisioningReceiver` **pozostaje** — dodatkowa, addytywna ścieżka (ADB), nie usuwana ani zastępowana; nowy ekran to alternatywa dla użytkownika końcowego, nie zamiennik narzędzia deweloperskiego.
- Zapis = zatrzymanie i ponowne uruchomienie `GatewayForegroundService` (`stopService`+`startForegroundService`) — nie żywa podmiana konfiguracji działającego adaptera (jak `SwappableGatewayInbound` z Fazy 7, Iteracja 7.7). Restart usługi to już znany, bezpieczny mechanizm (identyczny do tego, co dzieje się przy zabiciu procesu przez system — `START_STICKY`).

## Konsekwencje

- Zero zmian w `:domain` — cała praca mieści się w `:platform-android` (nowy layout, amendment `MainActivity.kt`/`GatewayForegroundService.kt`).
- `EmailAdapterConfiguration` na Androidzie i na `:platform-jvm` mają teraz tę samą, konfigurowalną strukturę host/port — spójność między dwoma punktami kompozycji tego samego adaptera.
- Pełny panel administracyjny dostępny przez sieć z telefonu pozostaje świadomie odłożony (Architectural Debt) — wymaga osobnej decyzji o przyjęciu nowej biblioteki HTTP dla Androida i przebudowie transportu Adaptera REST, do podjęcia z nadzorcą projektu odrębnie, gdy zdecyduje się to podjąć.
- Statyczna referencja `companion object instance` jest świadomym uproszczeniem ograniczonym do jednego konsumenta w jednym procesie — nie wzorzec do bezrefleksyjnego powielania gdyby pojawił się drugi, niezależny konsument stanu serwisu.

## Dokumenty powiązane

- 90-ADR/ADR-0002-UI-jako-klient-adaptera.md (rozgraniczenie zakresu — ten ekran nie jest jego przedmiotem)
- 90-ADR/ADR-0003-Domyslna-aplikacja-SMS-MMS.md (pierwotna decyzja o braku tradycyjnego UI konwersacji)
- 90-ADR/ADR-0036-Platforma-JVM-Kompozycja.md (precedens: konfigurowalny host/port SMTP/IMAP, ta sama technika)
- 40-Platforms/40-Android.md
