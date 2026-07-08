# midoMAIL 2.0 Documentation

## Struktura

- 00-Foundation – wizja, słownik, zasady architektoniczne
- 10-Core – modele domenowe i architektura Gateway
- 20-Adapters – kontrakty adapterów i konfiguracja
- 30-Infrastructure – konfiguracja, monitoring, bezpieczeństwo
- 40-Platforms – wymagania platformowe
- 50-Quality – jakość, testy, roadmap
- 60-User-Interface – projekt funkcjonalny UI
- 90-ADR – decyzje architektoniczne
- 91-Specification – szczegółowe kontrakty techniczne
- 92-Review – wyniki przeglądów

## Zasady
1. Single Source of Truth.
2. Dokumenty odwołują się do siebie zamiast powielać treść.
3. UI korzysta wyłącznie z kontraktów Core poprzez Adapter REST/CLI.
4. Zmiany architektury wymagają aktualizacji dokumentów źródłowych oraz odpowiednich specyfikacji.
