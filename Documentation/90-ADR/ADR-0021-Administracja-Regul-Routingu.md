# ADR-0021 — Administracja reguł routingu w czasie działania

**Status:** Accepted
**Data:** 2026-07-06

## Kontekst

`RoutingEngine` (`domain/routing/RoutingEngine.kt`) przyjmuje `List<RoutingRule>` wyłącznie w konstruktorze — niemutowalne po utworzeniu, zgodnie z zamrożonym kontraktem Fazy 1 (Public API Freeze Report, Traceability Matrix: „Zmiana wymaga nowego ADR"). 60-User-Interface/63-Routing.md §3 wymaga jednak operacji administracyjnych w czasie działania: „Dodaj regułę, Edytuj regułę, Wyłącz/Włącz, Zmień priorytet reguły... Historia zmian" — a §5: „Każda zmiana reguł jest wersjonowana i audytowana."

Ten sam problem co `Registry`/`GatewayEngine` w poprzednich fazach: potrzeba mutowalności w miejscu, które jest świadomie, architektonicznie zamrożone jako niemutowalne.

## Decyzja

Nowy port `RoutingRuleAdministration` w `:domain`, właściciel mutowalnej, wersjonowanej listy `RoutingRule` — `RoutingEngine` sam pozostaje całkowicie nietknięty:

```kotlin
class RoutingRuleAdministration(initialRules: List<RoutingRule> = emptyList()) {
    fun list(): List<RoutingRule>
    fun add(rule: RoutingRule)
    fun update(ruleId: RuleId, updated: RoutingRule)
    fun remove(ruleId: RuleId)
    fun buildEngine(): RoutingEngine
}
```

`buildEngine()` konstruuje ŚWIEŻĄ, niemutowalną instancję `RoutingEngine` z bieżącego stanu listy — punkt kompozycji wywołuje ją ponownie po każdej zmianie i podmienia instancję używaną przez `GatewayEngine`. Nie ma potrzeby oddzielnej metody „zmień priorytet"/„włącz-wyłącz" — to zwykłe `update()` z odpowiednio zmienionym polem `priority`/`enabled` istniejącego `RoutingRule` (oba pola już istnieją w modelu z Fazy 1).

`update()` automatycznie inkrementuje `RuleVersion` (63-Routing.md §5: „każda zmiana... jest wersjonowana") — wywołujący nie musi ręcznie zarządzać numeracją wersji.

## Konsekwencje

- `RoutingEngine` pozostaje w 100% niezmieniony — zero ryzyka regresji w istniejącym, sprawdzonym kodzie routingu.
- Punkt kompozycji (Iteracja 5.10+) musi pamiętać o przebudowie `RoutingEngine` po każdej zmianie — udokumentowane wprost jako odpowiedzialność wywołującego, nie automatyczne.
- Audyt zmian (63-Routing.md §5) realizowany przez tę samą infrastrukturę co inne operacje administracyjne (`EventCategory.ADMINISTRATIVE`, Iteracja 5.16) — nie duplikowany mechanizm.
- „Testuj regułę na przykładowym GatewayMessage" (symulator, 63-Routing.md §4) jest osobną operacją odczytu (wywołanie `RoutingEngine.route()` na przykładowej wiadomości, bez efektów ubocznych) — nie częścią tego portu, rozstrzygane w Iteracji 5.13 (REST: routing).

## Dokumenty powiązane

- 10-Core/13-Routing.md
- 60-User-Interface/63-Routing.md
- 91-Specification/SPEC-0007-Routing-Contract.md
- 91-Specification/SPEC-0024-Administrative-API-Contract.md
