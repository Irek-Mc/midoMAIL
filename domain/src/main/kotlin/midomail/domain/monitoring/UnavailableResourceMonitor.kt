package midomail.domain.monitoring

/**
 * Zaślepka `ResourceMonitor` (ADR-0027-Resource-Monitor.md, SA-14) — brak implementacji JVM w
 * Fazie 6 (`:platform-jvm` dopiero Faza 7). Ten sam wzorzec co `PushNotificationChannel` (Faza 4):
 * port istnieje już teraz, żeby Admin API mogło się do niego jednolicie odwoływać, zanim pojawi
 * się konkretna implementacja platformowa.
 */
class UnavailableResourceMonitor : ResourceMonitor {
    override fun snapshot(): ResourceSnapshot = ResourceSnapshot()
}
