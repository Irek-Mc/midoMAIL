package midomail.domain.port

import midomail.domain.message.DataReference

/**
 * Port przechowywania danych binarnych załączników (10-Core/18-Porty.md, §4;
 * ADR-0013-Attachment-Store.md; SPEC-0016-Attachment-Store-Contract.md).
 *
 * W przeciwieństwie do [MessageStore] — **bez gwarancji trwałości** między restartami procesu
 * (SPEC-0001-GatewayMessage.md, §Payload: „Gateway nie przechowuje danych trwale poza czasem
 * przetwarzania").
 */
interface AttachmentStore {
    fun write(data: ByteArray): DataReference
    fun read(reference: DataReference): ByteArray
}
