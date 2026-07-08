package midomail.domain.processing

/**
 * Stan przetwarzania komunikatu (SPEC-0004-Processing-State.md).
 *
 * [SCHEDULED] pełni podwójną rolę: standardowy harmonogram oraz oczekiwanie po throttlingu
 * (SPEC-0004, §Throttling a stan Scheduled; SPEC-0011-Rate-Limiting-Contract.md) — stąd
 * dozwolone jest przejście `SCHEDULED -> SCHEDULED`.
 *
 * [DELIVERED] jest pomijalny — nie każdy transport potwierdza dostarczenie
 * (SPEC-0004, §Rozszerzone stany dostarczenia: „jeśli transport je obsługuje") — stąd
 * `SENT` może przejść bezpośrednio do `COMPLETED`, z pominięciem `DELIVERED`.
 */
enum class ProcessingState {
    ACCEPTED,
    VALIDATED,
    ROUTED,
    SCHEDULED,
    PROCESSING,
    HANDED_TO_ADAPTER,
    SENT,
    DELIVERED,
    COMPLETED,
    FAILED,
    CANCELLED
}

private val ALLOWED_TRANSITIONS: Map<ProcessingState, Set<ProcessingState>> = mapOf(
    ProcessingState.ACCEPTED to setOf(
        ProcessingState.VALIDATED, ProcessingState.FAILED, ProcessingState.CANCELLED
    ),
    ProcessingState.VALIDATED to setOf(
        ProcessingState.ROUTED, ProcessingState.FAILED, ProcessingState.CANCELLED
    ),
    ProcessingState.ROUTED to setOf(
        ProcessingState.SCHEDULED, ProcessingState.FAILED, ProcessingState.CANCELLED
    ),
    ProcessingState.SCHEDULED to setOf(
        ProcessingState.SCHEDULED, ProcessingState.PROCESSING, ProcessingState.FAILED, ProcessingState.CANCELLED
    ),
    ProcessingState.PROCESSING to setOf(
        ProcessingState.HANDED_TO_ADAPTER, ProcessingState.FAILED, ProcessingState.CANCELLED
    ),
    ProcessingState.HANDED_TO_ADAPTER to setOf(
        ProcessingState.SENT, ProcessingState.FAILED, ProcessingState.CANCELLED
    ),
    ProcessingState.SENT to setOf(
        ProcessingState.DELIVERED, ProcessingState.COMPLETED, ProcessingState.FAILED, ProcessingState.CANCELLED
    ),
    ProcessingState.DELIVERED to setOf(
        ProcessingState.COMPLETED
    ),
    ProcessingState.COMPLETED to emptySet(),
    ProcessingState.FAILED to emptySet(),
    ProcessingState.CANCELLED to emptySet()
)

/**
 * Sprawdza, czy przejście z tego stanu do [target] jest dozwolone (SPEC-0004-Processing-State.md,
 * §Reguły przejść: „Niedozwolone są przejścia omijające reguły domenowe", „Stan końcowy nie może
 * zostać zmieniony bez rozpoczęcia nowego procesu").
 */
fun ProcessingState.canTransitionTo(target: ProcessingState): Boolean =
    ALLOWED_TRANSITIONS.getValue(this).contains(target)
