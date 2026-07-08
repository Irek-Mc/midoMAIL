package midomail.domain.notification

import midomail.domain.health.Alert

/**
 * Zaślepka kanału Push (38-Powiadomienia.md §3: „możliwość platformowa... niedostępna na
 * platformach, które jej nie udostępniają"). Świadoma decyzja zakresu Fazy 4 (docs/traceability-matrix.md,
 * Iteracja 4.0) — brak specyfikacji FCM w dokumentacji (żaden dokument nie precyzuje konkretnego
 * mechanizmu), analogicznie do odroczenia pełnego cyklu życia zadania w SPEC-0013 „do fazy, w
 * której pojawi się konkretny konsument". Prawdziwa implementacja (np. FCM na Androidzie) dochodzi,
 * gdy pojawi się konkretna potrzeba — ten port istnieje już teraz, żeby routing (Iteracja 4.8) mógł
 * się do niego jednolicie odwoływać.
 */
class PushNotificationChannel : NotificationChannel {
    override fun deliver(alert: Alert): NotificationResult =
        NotificationResult.Unavailable("Push nie jest obsługiwany na tej platformie")
}
