# midoMAIL 2.0

# Dokument 38 — Powiadomienia

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument definiuje architekturę dostarczania powiadomień (00-Foundation/06-Glossary.md, hasła „Alert" i „Powiadomienie") do administratora poza interfejsem Gateway — kanały, konfigurację, routing według poziomu Alertu oraz eskalację przy braku potwierdzenia. Realizuje decyzję ADR-0007-Dostarczanie-Powiadomien.md.

---

# 2. Odpowiedzialność

Warstwa powiadomień odpowiada za:

- dostarczanie treści Alertu wygenerowanego przez Health Monitor (30-Infrastructure/35-Health-Monitor.md) do skonfigurowanych kanałów zewnętrznych,
- routing Alertu do odpowiednich kanałów na podstawie jego poziomu (Info/Warning/Error/Critical),
- eskalację, jeżeli Alert pozostaje niepotwierdzony przez skonfigurowany czas,
- raportowanie powodzenia/niepowodzenia samej dostawy powiadomienia.

Warstwa powiadomień nie generuje Alertów — to odpowiedzialność Health Monitor. Nie zawiera logiki biznesowej i nie wpływa na przetwarzanie komunikatów.

---

# 3. Kanały dostarczania

- **Email** — przez istniejący Adapter Email (20-Adapters/22-Adapter-Email.md); powiadomienie jest zwykłą wiadomością wysyłaną tym samym mechanizmem SMTP, bez odrębnej implementacji.
- **Push** — możliwość platformowa (zależna od platformy uruchomieniowej, np. Android/FCM — 40-Platforms/40-Android.md); niedostępna na platformach, które jej nie udostępniają (np. część wdrożeń JVM/Linux headless).
- **Webhook** — generyczne wywołanie HTTP POST z ustrukturyzowanym ładunkiem JSON opisującym Alert (poziom, źródło, czas, treść, status). Integracja z PagerDuty, OpsGenie, Slack i podobnymi systemami odbywa się wyłącznie tym kanałem (ADR-0007-Dostarczanie-Powiadomien.md) — bez dedykowanych klientów tych systemów.

---

# 4. Routing Alert → kanał

Konfiguracja (91-Specification/SPEC-0005-Configuration-Model.md, §notifications) definiuje mapowanie poziomu Alertu na jeden lub więcej kanałów, np.:

- `CRITICAL` → Push + Webhook (natychmiast),
- `ERROR` → Email + Webhook,
- `WARNING` → Email,
- `INFO` → brak powiadomienia zewnętrznego (widoczne wyłącznie w Dashboard/Monitoring, 60-User-Interface/66-Monitoring.md).

Mapowanie jest w pełni konfigurowalne — powyższe są przykładem, nie wymogiem.

---

# 5. Eskalacja

Jeżeli Alert o poziomie `ERROR` lub `CRITICAL` pozostaje niepotwierdzony (60-User-Interface/66-Monitoring.md, §Operacje: „Potwierdzenie alertu") przez konfigurowalny czas, następuje eskalacja: powiadomienie jest wysyłane ponownie i/lub do dodatkowego kanału zdefiniowanego jako kolejny poziom eskalacji. Mechanizm czasowy eskalacji jest realizowany przez Scheduler (10-Core/16-Scheduler.md) jako zadanie cykliczne sprawdzające niepotwierdzone Alerty — nie przez odrębny, równoległy mechanizm czasowy.

Przykładowy model eskalacji (konfigurowalny, nie sztywny):

1. Poziom 0 (natychmiast): kanały zdefiniowane w §4.
2. Poziom 1 (po `escalationDelayMinutes` bez potwierdzenia): dodatkowy kanał lub powtórzenie.
3. Poziom 2 (po kolejnym takim samym okresie): kolejny kanał, jeśli skonfigurowany.

Eskalacja zatrzymuje się w momencie potwierdzenia Alertu przez administratora.

---

# 6. Niezawodność dostarczenia powiadomienia

Niepowodzenie dostarczenia samego powiadomienia (np. webhook zwrócił błąd) jest obsługiwane zgodnie z ogólną polityką Retry (30-Infrastructure/34-Error-Handling.md) i nigdy nie blokuje głównego przetwarzania komunikatów przez Gateway Engine — dostarczanie powiadomień jest procesem pobocznym względem rdzenia Communication Gateway.

---

# 7. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 10-Core/15-Event-Bus.md
- 10-Core/16-Scheduler.md
- 20-Adapters/22-Adapter-Email.md
- 30-Infrastructure/34-Error-Handling.md
- 30-Infrastructure/35-Health-Monitor.md
- 60-User-Interface/65-Konfiguracja.md
- 60-User-Interface/66-Monitoring.md
- 90-ADR/ADR-0007-Dostarczanie-Powiadomien.md
- 91-Specification/SPEC-0005-Configuration-Model.md
