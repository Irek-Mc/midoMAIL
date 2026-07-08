# midoMAIL 2.0

# Dokument 22 — Adapter Email

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę adaptera Email odpowiedzialnego za integrację Communication Gateway z systemami poczty elektronicznej. Adapter tłumaczy komunikację opartą o protokoły pocztowe na kanoniczny model GatewayMessage i odwrotnie.

---

# 2. Odpowiedzialność

Adapter Email odpowiada za:

- odbieranie komunikatów z obsługiwanych protokołów pocztowych,
- wysyłanie komunikatów do systemów pocztowych,
- mapowanie danych do i z modelu GatewayMessage,
- obsługę zdarzeń związanych z komunikacją pocztową,
- raportowanie stanu pracy oraz błędów.

Adapter nie zawiera logiki biznesowej, nie podejmuje decyzji routingowych i nie przetwarza reguł domenowych.

---

# 3. Założenia architektoniczne

- komunikacja z Gateway odbywa się wyłącznie poprzez porty,
- implementacja jest całkowicie odseparowana od Gateway Engine,
- wszystkie komunikaty są mapowane do modelu GatewayMessage,
- adapter może być zastąpiony inną implementacją bez wpływu na rdzeń systemu.

---

# 4. Integracja z usługami pocztowymi

Adapter odpowiada za integrację z obsługiwanymi protokołami pocztowymi, zarządzanie połączeniami, uwierzytelnianiem oraz bezpiecznym przesyłaniem danych zgodnie z wymaganiami architektury.

---

# 5. Diagnostyka

Adapter powinien publikować informacje o stanie połączeń, błędach komunikacji, statystykach oraz dostępności usług zgodnie z kontraktami monitorowania.

---

# 6. Wymagania protokołów

- Rozróżnienie IMAPS i IMAP+STARTTLS odbywa się na podstawie portu oraz konfiguracji TLS.
- Dla Gmail otwarcie skrzynki w READ_ONLY nie oznacza wiadomości jako `\Seen`.
- Exactly Once nie może opierać się wyłącznie na fladze `\Seen`; adapter wykorzystuje RFC 5322 Message-ID jako ExternalReference.
- Wątkowanie odpowiedzi wykorzystuje Message-ID, In-Reply-To oraz References.

---

# 7. Obsługa załączników (MMS ↔ Email)

Adapter Email deklaruje możliwość `SupportsAttachments` w SupportedCapabilities (SPEC-0006-Adapter-Contract.md).

- **MMS → Email**: załączniki z Payload (obrazy i inne media MMS odebrane przez Adapter GSM) są mapowane na rzeczywiste załączniki MIME w wysyłanej wiadomości e-mail — dołączane bezpośrednio do treści, nie jako link do zewnętrznego zasobu. Gateway nie przechowuje trwale danych binarnych załącznika poza czasem przetwarzania; przechowywanie i dostęp do załącznika po jego dostarczeniu jest odpowiedzialnością skrzynki pocztowej odbiorcy (10-Core/12-GatewayMessage.md, §7 Payload).
- **Email → MMS**: załączniki obecne w odebranej odpowiedzi e-mail są mapowane na Payload.Attachments; jeżeli komunikat zostanie skierowany do kanału GSM, Adapter GSM decyduje o wysyłce jako MMS (SMS nie przenosi załączników) — patrz 20-Adapters/21-Adapter-GSM.md, §7.
- Rozmiar załącznika przekazywanego do Adaptera Email jest ograniczony limitem serwera SMTP (typowo dziesiątki MB) — znacznie powyżej typowego rozmiaru MMS (rzędu pojedynczych MB), więc nie stanowi praktycznego ograniczenia w tym kierunku.

---

# 8. Model konfiguracji adaptera

Adapter Email udostępnia następujące parametry konfiguracyjne:

- Adres e-mail
- Nazwa wyświetlana
- SMTP Host / Port / SSL / STARTTLS
- IMAP Host / Port / IMAPS / STARTTLS
- Login
- Hasło (Secret Store)
- Folder synchronizacji
- Interwał synchronizacji
- Timeout
- Retry Policy
- Test SMTP
- Test IMAP

---

# 9. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 10-Core/18-Porty.md
- 10-Core/19-Plugin-SDK.md
- 20-Adapters/21-Adapter-GSM.md
- 30-Infrastructure/31-Bezpieczenstwo.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
