package midomail.domain.health

/**
 * Wspólny punkt wejścia dla Alertów niezależnie od źródła (SPEC-0020-Health-Aggregation-Contract.md;
 * 00-Foundation/06-Glossary.md, hasło „Alert" — generowane przez Health Monitor LUB Error Handling).
 * Jeden port, nie dwie rozbieżne ścieżki.
 */
fun interface AlertSink {
    fun onAlert(alert: Alert)
}
