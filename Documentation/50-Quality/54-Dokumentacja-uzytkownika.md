# midoMAIL 2.0

# Dokument 54 — Dokumentacja użytkownika

**Wersja:** 2.0
**Status:** Zaakceptowany
**Autor:** Ci

---

# 1. Cel dokumentu

Dokument określa zakres dokumentacji przeznaczonej dla użytkowników platformy midoMAIL 2.0 korzystających z funkcji Communication Gateway oraz udostępnionych interfejsów.

---

# 2. Odbiorcy

Dokumentacja użytkownika jest przeznaczona dla:

- operatorów Gateway,
- integratorów systemów,
- twórców adapterów,
- użytkowników interfejsów administracyjnych,
- użytkowników API i CLI.

---

# 3. Zakres dokumentacji

Dokumentacja użytkownika powinna obejmować:

- rozpoczęcie pracy z platformą,
- konfigurację podstawową,
- korzystanie z dostępnych adapterów,
- opis interfejsów użytkownika i API,
- rozwiązywanie typowych problemów,
- dobre praktyki eksploatacyjne,
- najczęściej zadawane pytania.

---

# 4. Szkielet: pierwsza konfiguracja adaptera

Minimalna ścieżka od zainstalowanej platformy do pierwszego działającego adaptera — pełna treść instruktażowa powstanie przed Fazą 6 Roadmapy (50-Quality/55-Roadmap.md), poniżej szkielet kroków i odwołania do dokumentów źródłowych, z których każdy krok wynika.

1. **Otwórz ekran Konfiguracja** (60-User-Interface/65-Konfiguracja.md) i wybierz „Dodaj adapter".
2. **Wybierz typ adaptera** (Email, GSM, REST, WebSocket, CLI) — formularz kreatora odpowiada polom `adapters[].config` ze schematu (91-Specification/SPEC-0005-Configuration-Model.md, §Przykładowy plik konfiguracyjny; 60-User-Interface/64-Adaptery.md, §6).
3. **Wypełnij pola wymagane dla wybranego typu** — np. dla Email: adres, host/port SMTP, host/port IMAP, hasło (zapisywane wyłącznie w bezpiecznym magazynie sekretów, nigdy jawnie — 30-Infrastructure/31-Bezpieczenstwo.md).
4. **Użyj „Test połączenia"** przed zapisaniem — błąd walidacji krzyżowej (np. brak wymaganego pola dla danego typu adaptera) jest zgłaszany od razu, zgodnie z SPEC-0005-Configuration-Model.md, §Walidacja krzyżowa.
5. **Zapisz i włącz adapter** — Registry Adapterów przeprowadza go przez cykl życia Registered → Initializing → Ready (10-Core/14-Registry-Adapterow.md, §4); stan widoczny natychmiast na Dashboard (60-User-Interface/61-Dashboard.md).
6. **Dodaj regułę routingu** kierującą komunikaty z/do nowego adaptera (60-User-Interface/63-Routing.md) — bez reguły pasującej komunikat nie zostanie donikąd dostarczony.
7. **Zweryfikuj w Diagnostyce** (60-User-Interface/67-Diagnostyka.md), wyszukując po ExternalReference lub MessageId pierwszego testowego komunikatu.

---

# 5. Wymagania

Dokumentacja powinna być:

- zgodna z aktualną wersją platformy,
- zrozumiała dla odbiorców o różnym poziomie wiedzy,
- oparta na rzeczywistych scenariuszach użycia,
- aktualizowana wraz ze zmianami funkcjonalnymi.

---

# 6. Dokumenty powiązane
- 00-Foundation/06-Glossary.md
- 20-Transporty
- 23-Adapter-REST
- 25-Adapter-CLI
- 30-Konfiguracja
- 53-Dokumentacja-administratora
- 55-Roadmap
- 60-User-Interface/61-Dashboard.md
- 60-User-Interface/63-Routing.md
- 60-User-Interface/64-Adaptery.md
- 60-User-Interface/65-Konfiguracja.md
- 60-User-Interface/67-Diagnostyka.md
- 91-Specification/SPEC-0005-Configuration-Model.md
