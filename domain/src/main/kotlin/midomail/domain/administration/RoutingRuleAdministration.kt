package midomail.domain.administration

import midomail.domain.routing.RoutingEngine
import midomail.domain.routing.RoutingRule
import midomail.domain.routing.RuleId
import midomail.domain.routing.RuleVersion
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/** Rodzaj zmiany reguły routingu (ADR-0029-Routing-Change-History.md). */
enum class RoutingRuleChangeType { ADDED, UPDATED, REMOVED }

/**
 * Wpis historii zmian (ADR-0029, 63-Routing.md §3 „Historia zmian"). [version] jest `null` dla
 * [RoutingRuleChangeType.REMOVED] — usunięta reguła nie ma już wersji do zaraportowania.
 */
data class RoutingRuleChange(
    val ruleId: RuleId,
    val changeType: RoutingRuleChangeType,
    val version: RuleVersion?,
    val timestamp: Instant
)

/**
 * Administracja regułami routingu w czasie działania (ADR-0021-Administracja-Regul-Routingu.md) —
 * `RoutingEngine` pozostaje całkowicie nietknięty, zamrożony (Faza 1). Ten port jest właścicielem
 * mutowalnej, wersjonowanej listy [RoutingRule]; [buildEngine] konstruuje świeżą, niemutowalną
 * instancję `RoutingEngine` z bieżącego stanu — punkt kompozycji odpowiada za wywołanie jej
 * ponownie po każdej zmianie i podmianę instancji używanej przez `GatewayEngine`.
 */
class RoutingRuleAdministration(initialRules: List<RoutingRule> = emptyList()) {

    private val rules = CopyOnWriteArrayList(initialRules)
    private val changeLog = CopyOnWriteArrayList<RoutingRuleChange>()

    fun list(): List<RoutingRule> = rules.toList()

    /** Migawka historii zmian w kolejności chronologicznej — najstarsze pierwsze (ADR-0029). */
    fun history(): List<RoutingRuleChange> = changeLog.toList()

    fun add(rule: RoutingRule) {
        require(rules.none { it.ruleId == rule.ruleId }) { "Reguła ${rule.ruleId.value} już istnieje" }
        rules.add(rule)
        changeLog.add(RoutingRuleChange(rule.ruleId, RoutingRuleChangeType.ADDED, rule.version, Instant.now()))
    }

    /**
     * [updated] zastępuje regułę pod [ruleId] — `RuleVersion` jest automatycznie inkrementowana
     * (63-Routing.md §5: „każda zmiana reguł jest wersjonowana"), wywołujący nie musi zarządzać
     * numeracją ręcznie. Pole `ruleId` w [updated] jest ignorowane — reguła zawsze zachowuje [ruleId]
     * przekazany jako pierwszy argument.
     */
    fun update(ruleId: RuleId, updated: RoutingRule) {
        val index = rules.indexOfFirst { it.ruleId == ruleId }
        require(index >= 0) { "Reguła ${ruleId.value} nie istnieje" }
        val currentVersion = rules[index].version.value.toIntOrNull() ?: 0
        val newVersion = RuleVersion((currentVersion + 1).toString())
        rules[index] = updated.copy(ruleId = ruleId, version = newVersion)
        changeLog.add(RoutingRuleChange(ruleId, RoutingRuleChangeType.UPDATED, newVersion, Instant.now()))
    }

    fun remove(ruleId: RuleId) {
        val removed = rules.removeIf { it.ruleId == ruleId }
        if (removed) {
            changeLog.add(RoutingRuleChange(ruleId, RoutingRuleChangeType.REMOVED, null, Instant.now()))
        }
    }

    fun buildEngine(): RoutingEngine = RoutingEngine(rules.toList())
}
