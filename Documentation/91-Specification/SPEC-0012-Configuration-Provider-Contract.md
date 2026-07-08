# SPEC-0012 — Configuration Provider Contract

**Status:** Accepted
**Powiązane dokumenty:** 10-Core/18-Porty.md, 30-Infrastructure/30-Konfiguracja.md, SPEC-0002-Porty.md, SPEC-0005-Configuration-Model.md

---

# Cel

Dokument definiuje techniczną sygnaturę portu `ConfigurationProvider` — dotąd odłożoną przez SPEC-0002-Porty.md, §Plan dalszej specyfikacji. Ujawniło się to jako luka blokująca implementację Fazy 1 (Iteracja 4b).

---

# Zakres w Fazie 1

`SPEC-0005-Configuration-Model.md` opisuje bogaty, zagnieżdżony schemat YAML (gateway/routing/adapters/scheduler/security/monitoring/messageStore/notifications), ale to mapowanie na w pełni typowane obiekty Kotlin jest zadaniem warstwy infrastruktury konkretnej platformy (Faza 3+ — parser YAML, adaptery), nie Core w Fazie 1 (10-Core/18-Porty.md; 50-Quality/55-Roadmap.md — „nie wybiegaj do przodu"). Faza 1 nie zawiera żadnych adapterów, więc żaden konsument nie potrzebuje jeszcze pełnego typowanego modelu konfiguracji.

---

# Interfejs

```kotlin
interface ConfigurationProvider {
    fun getValue(key: String): String?
}
```

`key` odpowiada ścieżce w hierarchii YAML (np. `gateway.instanceId`, `adapters.0.config.smtp.host`) — dokładny format ścieżki pozostaje otwarty i zostanie doprecyzowany wraz z implementacją rzeczywistego parsera konfiguracji (poza zakresem Fazy 1).

---

# Zasady zgodności

Rozszerzenie o w pełni typowany dostęp do konfiguracji (np. `getConfiguration(): GatewayConfiguration`) wymaga nowego ADR — nie jest wykluczone w przyszłości, ale nie jest częścią minimalnego kontraktu Fazy 1.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/18-Porty.md
- 30-Infrastructure/30-Konfiguracja.md
- 50-Quality/55-Roadmap.md
- 91-Specification/SPEC-0002-Porty.md
- 91-Specification/SPEC-0005-Configuration-Model.md
