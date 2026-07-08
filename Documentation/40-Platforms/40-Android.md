# midoMAIL 2.0

# Dokument 40 — Platforma Android

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument opisuje rolę systemu Android jako jednej z platform uruchomieniowych Communication Gateway. Android jest warstwą infrastrukturalną i nie stanowi części domeny biznesowej.

---

# 2. Rola platformy

Platforma Android odpowiada za udostępnienie usług systemowych wymaganych przez adaptery i komponenty infrastrukturalne, w szczególności:

- dostępu do modemu GSM,
- usług sieciowych,
- zarządzania cyklem życia procesu,
- bezpiecznego przechowywania danych,
- integracji z mechanizmami systemowymi.

---

# 3. Zasady architektoniczne

- Core nie zależy od Androida.
- Kod specyficzny dla Androida znajduje się wyłącznie w warstwie platformy i adapterów.
- Komunikacja z rdzeniem odbywa się przez porty.
- Możliwość zastąpienia implementacji platformy nie może wymagać zmian w domenie.

---

# 4. Ograniczenia

Logika biznesowa, routing oraz model domenowy nie mogą wykorzystywać klas frameworka Android.

---

# 5. Wymagania wynikające z Android

- Adapter GSM musi obsługiwać wiadomości wieloczęściowe (Multipart SMS).
- Potwierdzenie wysłania i dostarczenia musi opierać się na PendingIntent SENT oraz DELIVERED; brak wyjątku nie oznacza sukcesu.
- Architektura musi uwzględniać ograniczenia Doze Mode i wykorzystywać Foreground Service dla pracy 24/7, jeśli wymagają tego adaptery.
- Model uprawnień runtime (SEND_SMS, RECEIVE_SMS i powiązane) jest elementem architektury platformy.

---

# 6. Rola domyślnej aplikacji SMS/MMS

midoMAIL **zakłada z góry** rolę domyślnej aplikacji SMS/MMS na Androidzie (ADR-0003) — nie jest to opcja rozważana warunkowo, lecz wymóg obowiązujący od Fazy 3 Roadmapy. midoMAIL zastępuje systemową obsługę SMS/MMS, a nie uzupełnia jej.

Wynikające z tego wymagania platformowe:

- Żądanie roli domyślnej aplikacji SMS w czasie działania (`RoleManager.ROLE_SMS` na nowszych wersjach Androida, `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT` na starszych) jest wymaganym elementem uruchomienia aplikacji, nie funkcją opcjonalną.
- Manifest musi zawierać pełny zestaw komponentów wymaganych przez system dla tej roli, nawet jeśli midoMAIL nie prezentuje tradycyjnego UI konwersacji:
  - odbiornik `SMS_DELIVER_ACTION` (zamiast `SMS_RECEIVED_ACTION`, dostępnego dla aplikacji niedomyślnych),
  - odbiornik `WAP_PUSH_DELIVER_ACTION` (odbiór MMS),
  - aktywność obsługująca `ACTION_SENDTO`,
  - obsługa „Quick Response" (`RESPOND_VIA_MESSAGE_ACTION`).
- Bez roli domyślnej aplikacji odbiór MMS nie jest gwarantowany przez platformę — Adapter GSM nie może polegać na odbiorze MMS jako niedomyślna aplikacja.

---

# 7. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 18-Porty
- 21-Adapter-GSM
- 30-Konfiguracja
- 43-Przenosnosc
- 90-ADR/ADR-0003-Domyslna-aplikacja-SMS-MMS.md
