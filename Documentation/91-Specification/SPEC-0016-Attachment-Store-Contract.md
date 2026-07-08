# SPEC-0016 — Attachment Store Contract

**Status:** Accepted
**Powiązane dokumenty:** 90-ADR/ADR-0013-Attachment-Store.md, 10-Core/18-Porty.md, 91-Specification/SPEC-0001-GatewayMessage.md

---

# Cel

Dokument definiuje techniczny kontrakt portu `AttachmentStore`, wprowadzonego przez ADR-0013-Attachment-Store.md w celu rozwiązania `DataReference` (SPEC-0001-GatewayMessage.md, §Payload) do rzeczywistych danych binarnych załącznika.

---

# Interfejs

```kotlin
interface AttachmentStore {
    fun write(data: ByteArray): DataReference
    fun read(reference: DataReference): ByteArray
}
```

---

# Semantyka

- `write` przyjmuje surowe bajty załącznika i zwraca nową, unikalną `DataReference`. Wywoływane raz przez adapter odbierający dane z transportu, przed skonstruowaniem `Attachment`.
- `read` zwraca bajty zapisane wcześniej pod daną `DataReference`. Wywoływane raz przez adapter wysyłający komunikat.
- Brak gwarancji trwałości między restartami procesu (ADR-0013) — w przeciwieństwie do `MessageStore` (SPEC-0009), który gwarantuje trwałość. `read` dla `DataReference`, której dane zostały już usunięte przez implementację (po zakończeniu przetwarzania komunikatu), jest błędem — konkretna semantyka błędu (wyjątek vs wartość null) należy do implementacji i nie jest częścią minimalnego kontraktu portu.

---

# Dokumenty powiązane

- 00-Foundation/06-Glossary.md
- 90-ADR/ADR-0013-Attachment-Store.md
- 10-Core/18-Porty.md
- 91-Specification/SPEC-0001-GatewayMessage.md
