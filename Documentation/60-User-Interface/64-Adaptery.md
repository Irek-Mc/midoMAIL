# midoMAIL 2.0

# Dokument 64 — Zarządzanie adapterami

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel

Ekran umożliwia zarządzanie wszystkimi adapterami zarejestrowanymi w Communication Gateway.

---

# 2. Widok listy

Dla każdego adaptera prezentowane są:

- Nazwa i typ
- Wersja
- Stan (Running, Degraded, Stopped)
- Kanały komunikacji
- Ostatnia aktywność
- Health

---

# 3. Szczegóły adaptera

- Konfiguracja
- Obsługiwane możliwości
- Statystyki
- Ostatnie błędy
- Historia zdarzeń

---

# 4. Operacje

- Dodaj adapter
- Edytuj konfigurację
- Włącz/Wyłącz
- Restart
- Test połączenia
- Usuń

---

# 5. Wymagania dla Core

Core udostępnia pełny rejestr adapterów, ich stan, możliwości, metryki i historię zdarzeń poprzez stabilne kontrakty. UI nie odwołuje się bezpośrednio do implementacji adapterów.

---

# 6. Kreator konfiguracji adaptera

Każdy adapter posiada dedykowany formularz konfiguracji oparty na wspólnym modelu. Pola poniżej odpowiadają dokładnie sekcji `adapters[].config` z przykładowego pliku YAML (91-Specification/SPEC-0005-Configuration-Model.md, §Przykładowy plik konfiguracyjny) — formularz nie wprowadza pól, których nie ma w schemacie, i odwrotnie.

### Sekcja Ogólne
- Nazwa adaptera
- Opis
- Status (Włączony/Wyłączony)
- Kanały komunikacji

### Adapter Email — SMTP
- Adres e-mail nadawcy
- Nazwa wyświetlana
- Host SMTP
- Port SMTP
- Tryb: SSL / STARTTLS
- Login
- Hasło (przechowywane w bezpiecznym magazynie sekretów)
- Test połączenia SMTP

### Adapter Email — IMAP
- Host IMAP
- Port IMAP
- Tryb: IMAPS / STARTTLS
- Login
- Hasło
- Folder
- Interwał synchronizacji
- Test połączenia IMAP

### Adapter GSM
- Wybór modemu/SIM (jeśli platforma obsługuje multi-SIM — patrz 20-Adapters/21-Adapter-GSM.md, §Multi-SIM)
- Numer telefonu
- SMSC (opcjonalnie)
- Raporty dostarczenia
- Obsługa Multipart SMS
- Test wysyłki

### Adapter REST
- Endpoint
- Uwierzytelnianie
- Nagłówki
- Timeout
- Retry
- Test połączenia

### Adapter WebSocket
- URL
- TLS
- Heartbeat
- Auto reconnect
- Test połączenia

Każda zmiana konfiguracji jest walidowana przed zapisaniem i może zostać przetestowana bez uruchamiania całego Gateway.

---

# 7. Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 20-Adapters/21-Adapter-GSM.md
- 20-Adapters/22-Adapter-Email.md
- 20-Adapters/23-Adapter-REST.md
- 20-Adapters/24-Adapter-WebSocket.md
- 30-Infrastructure/30-Konfiguracja.md
- 91-Specification/SPEC-0005-Configuration-Model.md
