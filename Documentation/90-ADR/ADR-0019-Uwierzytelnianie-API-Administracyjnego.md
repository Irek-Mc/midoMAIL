# ADR-0019 — Uwierzytelnianie API administracyjnego

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

30-Infrastructure/31-Bezpieczenstwo.md §3 wymienia „uwierzytelnianie" i „autoryzację" jako obszary odpowiedzialności platformy, a 60-User-Interface/70-Uzytkownicy-i-uprawnienia.md §6 stwierdza „Core udostępnia model uwierzytelniania, autoryzacji i audytu" — ale żaden dokument nie precyzuje KONKRETNEGO mechanizmu (brak wzmianki o kluczach API, JWT, OAuth, sesjach, mTLS w całym drzewie dokumentacji). To realna luka bezpieczeństwa, nie stylistyczna: Adapter REST ma wystawić „pełny zestaw operacji administracyjnych" (Roadmap §7) — bez ŻADNEGO mechanizmu dostępu byłby to w pełni otwarty, niezabezpieczony interfejs administracyjny.

Pełny model wieloużytkownikowy z rolami (Administrator/Operator/Audytor/Integrator, 70-Uzytkownicy-i-uprawnienia.md §2-4) jest już jawnie zapowiedziany dopiero w Fazie 6, ograniczony do „profili wdrożenia serwerowego (JVM/Linux)" — Roadmap §8 pkt 4. Budowanie go teraz, w Fazie 5, wyprzedzałoby udokumentowany harmonogram bez uzasadnionego konsumenta (UI, który miałby z niego korzystać, jeszcze nie istnieje).

## Decyzja

Uwierzytelnianie API administracyjnego (Faza 5) realizowane przez **statyczny klucz API** przekazywany w nagłówku żądania (REST) / argumencie (CLI), walidowany względem wartości odczytanej z już istniejącego portu `SecretStore` (Faza 3, SPEC-0017-Secret-Store-Contract.md) — ten sam mechanizm co poświadczenia SMTP/IMAP/Keystore, żadna nowa infrastruktura.

Nowy port w `:domain`:

```kotlin
interface AdminAuthenticator {
    fun authenticate(providedKey: String): Boolean
}
```

Referencyjna implementacja czyta oczekiwany klucz z `SecretStore` pod ustaloną referencją (np. `"admin-api/key"`) i porównuje w czasie stałym (ochrona przed atakiem czasowym na porównanie ciągów — 31-Bezpieczenstwo.md §3 „bezpieczeństwo komunikacji" uzasadnia tę ostrożność, mimo braku jawnej wzmianki o tym konkretnym ataku).

**Autoryzacja (rozróżnienie ról) świadomie NIE wchodzi w zakres** — jeden klucz = pełny dostęp administracyjny, zgodnie z modelem „single-admin" domyślnym dla wdrożeń Faz 1–5 (Android jako jedyna dotąd zweryfikowana platforma, sam z natury single-user). Pełny RBAC pozostaje Fazą 6.

## Konsekwencje

- Zero nowych zależności zewnętrznych — reużycie istniejącego `SecretStore`.
- Brak ochrony przed atakiem typu brute-force na klucz (rate limiting żądań uwierzytelnienia) — świadomie poza zakresem, odnotowane jako dług architektoniczny w raporcie końcowym Fazy 5.
- Brak szyfrowania transportu (TLS) narzucanego przez tę decyzję — wybór protokołu (HTTP vs HTTPS) należy do ADR-0023 (wybór serwera HTTP, Iteracja 5.7), nie do tego dokumentu.
- Każda nieudana próba uwierzytelnienia jest audytowana jako zdarzenie domenowe (SPEC-0024-Administrative-API-Contract.md, §Uwierzytelnianie i audyt), reużywając `EventCategory.ADMINISTRATIVE` z Fazy 4 — nie nowy mechanizm.

## Dokumenty powiązane

- 30-Infrastructure/31-Bezpieczenstwo.md
- 60-User-Interface/70-Uzytkownicy-i-uprawnienia.md
- 91-Specification/SPEC-0005-Configuration-Model.md
- 91-Specification/SPEC-0017-Secret-Store-Contract.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
