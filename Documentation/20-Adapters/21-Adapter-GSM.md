# midoMAIL 2.0

# Dokument 21 — Adapter GSM

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę adaptera GSM odpowiedzialnego za integrację Communication Gateway z siecią GSM. Adapter pełni rolę warstwy translacji pomiędzy usługami platformy a kanonicznym modelem GatewayMessage.

---

# 2. Odpowiedzialność

Adapter GSM odpowiada za:

- odbieranie komunikatów z sieci GSM,
- wysyłanie komunikatów do sieci GSM,
- mapowanie danych do i z modelu GatewayMessage,
- obsługę zdarzeń platformowych związanych z GSM,
- raportowanie stanu pracy do komponentów monitorujących.

Adapter nie zawiera logiki biznesowej, nie realizuje routingu i nie podejmuje decyzji domenowych.

---

# 3. Założenia architektoniczne

- komunikacja z Gateway odbywa się wyłącznie przez zdefiniowane porty,
- implementacja jest odseparowana od Gateway Engine,
- wszystkie dane są mapowane do modelu GatewayMessage,
- adapter może zostać zastąpiony inną implementacją bez wpływu na rdzeń systemu.

---

# 4. Integracja z platformą

Adapter korzysta z usług platformy odpowiedzialnych za dostęp do modemu GSM. Warstwa platformowa udostępnia wyłącznie funkcje infrastrukturalne i pozostaje poza domeną biznesową.

---

# 5. Diagnostyka

Adapter powinien publikować informacje o stanie, błędach, dostępności modemu oraz statystykach komunikacji zgodnie z kontraktami platformy.

---

# 6. Wymagania implementacyjne — SMS

Adapter GSM musi korzystać z Multipart SMS dla długich wiadomości, wykorzystywać PendingIntent SENT/DELIVERED jako źródło stanu dostarczenia oraz raportować rzeczywisty wynik operacji do Gateway.

Treść przekraczająca skonfigurowany maksymalny limit segmentów Multipart jest obcinana z jawnym wskaźnikiem obcięcia, nie odrzucana w całości — pełna decyzja i uzasadnienie: 90-ADR/ADR-0009-Obciecie-Tresci-SMS.md.

---

# 7. Wymagania implementacyjne — MMS

MMS jest pełnoprawnym, odrębnym transportem obsługiwanym przez Adapter GSM obok SMS — nie jest traktowany jako rozszerzenie SMS (ADR-0003).

- Adapter GSM odbiera i wysyła MMS, mapując załączniki na model Payload z załącznikami (10-Core/12-GatewayMessage.md, §ExternalReference i Attachments; 91-Specification/SPEC-0001-GatewayMessage.md).
- Odbiór MMS wymaga roli domyślnej aplikacji SMS/MMS (40-Platforms/40-Android.md, §6; ADR-0003) — bez tej roli platforma nie gwarantuje dostarczenia zdarzenia o przychodzącym MMS do adaptera.
- Adapter GSM raportuje SupportsAttachments/SupportsMms jako możliwości (SupportedCapabilities, SPEC-0006-Adapter-Contract.md), tak aby Routing Engine mógł uwzględnić brak tej możliwości przy wyznaczaniu trasy dla komunikatu z załącznikiem.

---

# 8. Multi-SIM

Obsługa wielu kart SIM jest opcjonalną możliwością implementacji adaptera GSM. Kontrakt adaptera definiuje wybór aktywnego kanału GSM, natomiast dostępność tej funkcji zależy od platformy sprzętowej i systemowej.

---

# 9. Brak zasięgu (offline)

Brak zasięgu sieci GSM nie wymaga odrębnego mechanizmu ponad już istniejące: Adapter GSM zgłasza `Degraded` w Registry Adapterów (10-Core/14-Registry-Adapterow.md, §4), gdy platforma raportuje brak łączności. Komunikaty wychodzące pozostają w Processing State `Scheduled` i są ponawiane przez Scheduler (10-Core/16-Scheduler.md) na tych samych zasadach co throttling (91-Specification/SPEC-0011-Rate-Limiting-Contract.md) — nie giną i nie wymagają ręcznej interwencji. Wysyłka SMS/MMS przekazana do platformy przed utratą zasięgu pozostaje pod kontrolą platformy (wewnętrzne kolejkowanie radiowe) — poza bezpośrednią kontrolą Gateway; jedynym źródłem prawdy o jej losie pozostaje `PendingIntent SENT`/`DELIVERED` (§6). Odbiór (polling IMAP-odpowiednika dla GSM nie dotyczy — SMS/MMS przychodzące są dostarczane push przez platformę) wznawia się automatycznie po odzyskaniu zasięgu, bez dodatkowej logiki po stronie adaptera.

---

# 10. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/12-GatewayMessage.md
- 10-Core/14-Registry-Adapterow.md
- 10-Core/16-Scheduler.md
- 10-Core/18-Porty.md
- 10-Core/19-Plugin-SDK.md
- 40-Platforms/40-Android.md
- 90-ADR/ADR-0003-Domyslna-aplikacja-SMS-MMS.md
- 90-ADR/ADR-0009-Obciecie-Tresci-SMS.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0006-Adapter-Contract.md
- 91-Specification/SPEC-0011-Rate-Limiting-Contract.md
