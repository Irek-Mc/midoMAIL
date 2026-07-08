# SPEC-0017 — Secret Store Contract

**Status:** Accepted
**Powiązane dokumenty:** 30-Infrastructure/30-Konfiguracja.md, 30-Infrastructure/31-Bezpieczenstwo.md, 10-Core/18-Porty.md, 91-Specification/SPEC-0005-Configuration-Model.md

---

# Cel

Dokument definiuje techniczny kontrakt portu `SecretStore` — dotąd nigdzie niezdefiniowanego jako konkretny interfejs. 30-Infrastructure/30-Konfiguracja.md, §5 wymaga „bezpiecznego magazynu sekretów (np. Android Keystore)", a 91-Specification/SPEC-0005-Configuration-Model.md definiuje format *referencji* do sekretu (`credentials.secretRef`, np. `"email-primary/credentials"`) oraz dopuszczalne *wartości konfiguracyjne* wyboru implementacji (`security.secretStore: android-keystore | env | file`) — ale żaden dokument nie precyzował sygnatury portu, przez który Core/adaptery odczytują sekret wskazywany przez referencję. Ujawniło się to jako luka blokująca implementację Fazy 3 (Iteracja 3.1, Android Keystore).

---

# Interfejs

```kotlin
interface SecretStore {
    fun read(reference: String): String?
    fun write(reference: String, value: String)
}
```

`reference` odpowiada dokładnie formatowi już ustalonemu w SPEC-0005 (`credentials.secretRef`, np. `"email-primary/credentials"`) — port nie interpretuje jego wewnętrznej struktury (nie zakłada separatora `/` ani znaczenia segmentów), to szczegół implementacji (np. Android Keystore może użyć całej referencji jako aliasu klucza).

`read` zwraca `null`, jeśli pod daną referencją nie zapisano jeszcze żadnego sekretu — nie jest to błąd (analogicznie do `ConfigurationProvider.getValue`, SPEC-0012). `write` nadpisuje istniejącą wartość pod tą samą referencją, jeśli już istnieje.

---

# Zasady zgodności

- Implementacja `android-keystore` (Faza 3) przechowuje sekrety w Android Keystore System — klucz szyfrujący nigdy nie opuszcza zaufanego środowiska sprzętowego/systemowego urządzenia.
- Implementacje `env`/`file` (JVM/Linux, 40-Platforms/41-JVM.md, 42-Linux.md) pozostają poza zakresem Fazy 3 — SecretStore jest portem, konkretne implementacje inne niż Android Keystore powstają w fazie, w której będą potrzebne (np. przy przenoszeniu Adaptera Email z harnessu na docelową platformę JVM/Linux).
- Rozszerzenie kontraktu (np. usuwanie sekretu, listowanie referencji) wymaga nowego ADR.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/18-Porty.md
- 30-Infrastructure/30-Konfiguracja.md
- 30-Infrastructure/31-Bezpieczenstwo.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0012-Configuration-Provider-Contract.md
