package midomail.domain.processing

/**
 * Kontekst przetwarzania komunikatu — informacje niezbędne do realizacji routingu, polityki
 * Exactly Once, monitorowania oraz diagnostyki. Nie stanowi części danych biznesowych komunikatu
 * (10-Core/12-GatewayMessage.md, §10; SPEC-0001-GatewayMessage.md, §ProcessingContext).
 *
 * Nowo utworzony GatewayMessage rozpoczyna w stanie [ProcessingState.ACCEPTED]
 * (10-Core/11-Gateway-Engine.md, §5, krok 1: „Odebranie komunikatu przez port wejściowy").
 * Kolejne kroki przetwarzania w Gateway Engine tworzą nowe kopie z uaktualnionym stanem —
 * GatewayMessage jest niemutowalny.
 */
data class ProcessingContext(
    val processingState: ProcessingState = ProcessingState.ACCEPTED
)
