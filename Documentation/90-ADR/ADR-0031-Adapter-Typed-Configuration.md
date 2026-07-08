# ADR-0031 — Typowana konfiguracja adaptera

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`60-User-Interface/64-Adaptery.md` §6 wymaga „Kreatora konfiguracji adaptera" — dedykowanego formularza per typ adaptera, z polami odpowiadającymi dokładnie `EmailAdapterConfiguration`/`GsmAdapterConfiguration` (SMTP: `host`/`port`/`ssl`/`starttls`/`username`/`password`; IMAP: analogicznie + `folder`/`pollIntervalMillis`; GSM: `maxSmsSegments`/`forwardToAddress`). `ConfigurationProvider` (Faza 5) to płaski zapis klucz-wartość, bez świadomości typów.

**Odkrycie architektoniczne — naiwne podejście narusza granicę modułów:** `EmailAdapterConfiguration`/`GsmAdapterConfiguration` żyją odpowiednio w `:adapter-email`/`:adapter-gsm` — modułach SIOSTRZANYCH względem `:adapter-rest`/`:adapter-cli` (żaden nie zależy od drugiego, ustalone w Fazie 5, ADR-0022). Gdyby Admin API miało bezpośrednio operować na tych typach, `:adapter-rest`/`:adapter-cli` musiałyby zyskać zależność na `:adapter-email`/`:adapter-gsm` — złamanie tej samej granicy, którą Faza 5 celowo ustanowiła.

## Decyzja

**Generyczny, tekstowy schemat pól per typ adaptera** w `:domain` — nie typy Kotlin skopiowane/zaimportowane z modułów adapterów, wyłącznie nazwy pól jako `String` (ta sama filozofia co konwencjonalne nazwy kluczy `ConfigurationProvider` z Fazy 5):

```kotlin
object AdapterConfigurationSchema {
    fun fieldsFor(adapterType: String): List<String> = when (adapterType) {
        "email" -> EMAIL_FIELDS
        "gsm" -> GSM_FIELDS
        else -> emptyList()
    }
    val SECRET_FIELDS: Set<String> // "smtp.password", "imap.password" — nigdy nie zwracane przy odczycie
}

class AdapterConfigurationAdministration(private val configurationProvider: ConfigurationProvider) {
    fun read(adapterId: AdapterId, adapterType: String): Map<String, String?>
    fun write(adapterId: AdapterId, adapterType: String, field: String, value: String)
}
```

Klucze w `ConfigurationProvider` mają konwencjonalny format `adapters.{adapterId}.{pole}` (np. `adapters.email-primary.smtp.host`). `write()` waliduje, że `field` należy do `fieldsFor(adapterType)` — odrzuca nieznane pole (nie tworzy dowolnych kluczy).

**Pola sekretne nigdy nie są zwracane przez `read()`** (65-Konfiguracja.md §4: „Hasła i klucze nigdy nie są wyświetlane w jawnej postaci") — `read()` zwraca dla nich zawsze `null`, niezależnie od tego, czy wartość jest ustawiona. Zapis (`write()`) nadal działa normalnie dla pól sekretnych — formularz UI (Iteracja 6.25) renderuje je jako pola tylko-do-zapisu.

## Konsekwencje

- `:adapter-rest`/`:adapter-cli` nadal zależą wyłącznie od `:domain` — granica modułów z Fazy 5 nienaruszona.
- Schemat pól (`EMAIL_FIELDS`/`GSM_FIELDS`) jest ręcznie utrzymywaną kopią kształtu `EmailAdapterConfiguration`/`GsmAdapterConfiguration` — ryzyko rozjazdu przy przyszłej zmianie tych typów w odpowiednich modułach adapterów. Świadomie zaakceptowane (ten sam kompromis co `ConfigurationProvider` niewiedzący nic o strukturze — Faza 5), nie automatyczna synchronizacja.
- Dodanie nowego typu adaptera (np. WebSocket, Faza 7) wymaga wyłącznie rozszerzenia `AdapterConfigurationSchema.fieldsFor()` o nowy `when`-branch — `AdapterConfigurationAdministration` sam nie wymaga zmian.
- Rzeczywisty zapis do `SmtpConfig`/`ImapConfig`/`GsmAdapterConfiguration` (odczyt tych kluczy przez punkt kompozycji przy starcie adaptera) pozostaje odpowiedzialnością punktu kompozycji (`platform-android`), poza zakresem tego ADR — ten dokument dotyczy wyłącznie WARSTWY ADMINISTRACYJNEJ (odczyt/zapis przez UI), nie mechanizmu ładowania konfiguracji przy starcie.

## Dokumenty powiązane

- 20-Adapters/22-Adapter-Email.md
- 20-Adapters/21-Adapter-GSM.md
- 60-User-Interface/64-Adaptery.md
- 60-User-Interface/65-Konfiguracja.md
- 90-ADR/ADR-0020-Konfiguracja-Zapis.md
- 90-ADR/ADR-0022-Moduly-Adapter-REST-CLI.md
- 91-Specification/SPEC-0025-UI-Client-Contract.md
