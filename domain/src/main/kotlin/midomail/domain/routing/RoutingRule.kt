package midomail.domain.routing

import midomail.domain.adapter.AdapterId
import midomail.domain.message.ChannelType
import midomail.domain.message.MessagePriority

@JvmInline
value class RuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "RuleId nie może być pusty" }
    }
}

@JvmInline
value class DeliveryPolicy(val value: String) {
    init {
        require(value.isNotBlank()) { "DeliveryPolicy nie może być pusty" }
    }
}

@JvmInline
value class RuleVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "RuleVersion nie może być pusty" }
    }
}

/**
 * Warunki dopasowania reguły routingu. Odwołują się wyłącznie do [ChannelType] — jedynego pola,
 * na podstawie którego Routing Engine podejmuje decyzje (ADR-0010-Model-Channel.md). Nigdy
 * `address` ani `AdapterId` (ADR-0012-Channel-AdapterId.md; SPEC-0005-Configuration-Model.md,
 * §Walidacja krzyżowa).
 *
 * `sourceChannel == null`/`destinationChannel == null` oznacza brak warunku (dopasowuje każdą
 * wartość odpowiedniego pola).
 *
 * [destinationChannel] dodany w Fazie 3 (Iteracja 3.13) — amendment analogiczny do
 * `attachmentStore`/`eventPublisher` w `AdapterPorts`. Bez niego reguła kierująca wątek
 * międzykanałowy z powrotem (np. odpowiedź e-mail → SMS) dopasowywałaby KAŻDĄ wiadomość danego
 * kanału źródłowego, nie tylko tę, której `destination` faktycznie wskazuje na kanał docelowy
 * reguły — znalezione na urządzeniu: reguła `email→gsm` oparta wyłącznie o `sourceChannel`
 * dopasowywała też niepowiązane e-maile w skrzynce (niebędące odpowiedzią na przekazaną
 * wiadomość z GSM), próbując wysłać je jako SMS pod ich własnym adresem e-mail jako
 * `destination.address` (błąd `NULL_PDU` — nieprawidłowy format adresu dla SMS).
 */
data class RoutingConditions(
    val sourceChannel: ChannelType? = null,
    val destinationChannel: ChannelType? = null
)

/**
 * Reguła routingu (10-Core/13-Routing.md, §Model reguł routingu; SPEC-0007-Routing-Contract.md).
 *
 * Reguły są ewaluowane w kolejności malejącego [priority]; pierwsza pasująca reguła z
 * `enabled == true` wyznacza decyzję routingu (10-Core/13-Routing.md). [setPriority] — jeśli
 * obecne — nadpisuje `MessagePriority` komunikatu; to odrębne pojęcie od [priority] reguły
 * (ADR-0005-Message-Priority.md, uwaga terminologiczna).
 */
data class RoutingRule(
    val ruleId: RuleId,
    val priority: Int,
    val enabled: Boolean = true,
    val conditions: RoutingConditions = RoutingConditions(),
    val targetChannel: ChannelType,
    val targetAdapter: AdapterId,
    val deliveryPolicy: DeliveryPolicy,
    val setPriority: MessagePriority? = null,
    val version: RuleVersion
)
