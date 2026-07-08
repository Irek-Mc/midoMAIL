# ADR-0012 — Rozszerzenie Channel o AdapterId

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

91-Specification/SPEC-0009-Message-Store-Contract.md, §Schemat wymaga indeksu „AdapterId (Source, Destination)" — identyfikatora konkretnego, zarejestrowanego adaptera obsługującego źródło i cel komunikatu. Model `Channel` zdefiniowany w ADR-0010-Model-Channel.md (ChannelType + opcjonalny address) nie zawierał pola na `AdapterId`. Ujawniło się to jako luka blokująca implementację Fazy 1 (Iteracja 4a, port MessageStore) — kontrakt `GatewayMessage`/`Channel` był już wtedy zamrożony (Public API Freeze Report, Iteracja 1d), więc rozszerzenie wymaga jawnego ADR, nie cichej zmiany.

## Decyzja

`Channel` zostaje rozszerzony o pole `adapterId: AdapterId? = null`:

```kotlin
data class Channel(
    val type: ChannelType,
    val address: String? = null,
    val adapterId: AdapterId? = null
)
```

`AdapterId` dla `Source` jest znany od razu przy tworzeniu komunikatu (identyfikuje adapter, który odebrał komunikat z transportu). `AdapterId` dla `Destination` jest początkowo `null` i zostaje wypełniony wynikiem decyzji Routing Engine (`TargetAdapter`, SPEC-0007-Routing-Contract.md) — analogicznie do tego, jak `address` bywa nieznany dla Destination przed wyznaczeniem trasy (ADR-0010-Model-Channel.md).

`AdapterId` traktowany jest tak samo jak `address` — jako pole przenoszone przez Core bez interpretacji jego wartości poza celami indeksowania/filtrowania (Routing Engine może go odczytać jako wynik swojej własnej decyzji, ale nie warunkuje nim reguł — warunkiem reguły pozostaje wyłącznie `ChannelType`, zgodnie z ADR-0010).

## Konsekwencje

- To jest udokumentowana zmiana zamrożonego kontraktu `Channel` (Iteracja 1b/1d) — kolejne miejsca korzystające z `Channel` (Iteracja 4a i dalsze) budowane są już na tej rozszerzonej wersji.
- Message Store (SPEC-0009) może indeksować `source.adapterId` i `destination.adapterId` niezależnie od `type`/`address`.
- `AdapterId` jest tym samym typem identyfikatora, który Registry Adapterów (10-Core/14-Registry-Adapterow.md) i Plugin SDK (SPEC-0010-Plugin-SDK-Contract.md) wykorzystują do identyfikacji zarejestrowanego adaptera — jedno pojęcie, nie duplikat.

## Dokumenty powiązane

- 90-ADR/ADR-0010-Model-Channel.md
- 10-Core/12-GatewayMessage.md
- 10-Core/14-Registry-Adapterow.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0007-Routing-Contract.md
- 91-Specification/SPEC-0009-Message-Store-Contract.md
- 91-Specification/SPEC-0010-Plugin-SDK-Contract.md
