# ADR-0013 — Port AttachmentStore dla danych binarnych załączników

**Status:** Accepted
**Data:** 2026-07-05

## Kontekst

`Attachment.dataReference: DataReference` (SPEC-0001-GatewayMessage.md, §Payload, Iteracja 1c Fazy 1) jest nieprzezroczystą referencją do danych binarnych — „Gateway nie przechowuje danych trwale poza czasem przetwarzania" (SPEC-0001; 20-Adapters/22-Adapter-Email.md, §7). Żaden dokument nie definiował mechanizmu rozwiązania tej referencji do rzeczywistych bajtów. Ujawniło się to jako luka blokująca implementację Fazy 2 (Iteracja 2.2, mapowanie GatewayMessage → MIME) — bez rzeczywistych bajtów nie da się zbudować prawdziwego załącznika MIME ani, symetrycznie, przyjąć załącznika z odebranego e-maila.

## Decyzja

Nowy port Core `AttachmentStore`:

```kotlin
interface AttachmentStore {
    fun write(data: ByteArray): DataReference
    fun read(reference: DataReference): ByteArray
}
```

`write` jest wywoływane raz przez adapter odbierający załącznik z transportu (np. Adapter Email mapujący część MIME, Adapter GSM mapujący MMS), tworząc `DataReference` umieszczaną w `Attachment` przed skonstruowaniem `GatewayMessage`. `read` jest wywoływane raz przez adapter wysyłający komunikat, gdy potrzebuje rzeczywistych bajtów do zbudowania wiadomości transportu (np. MIME multipart).

Zgodnie z „Gateway nie przechowuje danych trwale poza czasem przetwarzania" — `AttachmentStore`, w przeciwieństwie do `MessageStore` (SPEC-0009), **nie gwarantuje trwałości**. Implementacja (szczegół infrastruktury, nieznany Core) odpowiada za przechowywanie danych przez czas życia przetwarzania komunikatu i za własne sprzątanie po nim — Core nie narzuca *kiedy* dane są usuwane, tylko że nie jest wymagana trwałość między restartami procesu.

## Konsekwencje

- `AttachmentStore` dołącza do kategorii portów infrastrukturalnych (10-Core/18-Porty.md, §3).
- Implementacja w Fazie 2 (JVM): przechowywanie w pamięci (analogicznie do pozostałych referencyjnych implementacji in-memory z Fazy 1).
- Implementacja w Fazie 3 (Android): może wykorzystać mechanizmy platformy (np. pliki tymczasowe, Content Resolver) — szczegół pozostaje poza `:domain`.
- Pełny kontrakt techniczny: SPEC-0016-Attachment-Store-Contract.md.

## Dokumenty powiązane

- 10-Core/12-GatewayMessage.md
- 10-Core/18-Porty.md
- 20-Adapters/22-Adapter-Email.md
- 91-Specification/SPEC-0001-GatewayMessage.md
- 91-Specification/SPEC-0016-Attachment-Store-Contract.md
