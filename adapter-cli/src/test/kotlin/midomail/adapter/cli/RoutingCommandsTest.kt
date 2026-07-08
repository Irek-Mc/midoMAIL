package midomail.adapter.cli

import midomail.domain.administration.RoutingRuleAdministration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Potwierdza komendy CLI administracji routingu (SPEC-0024, §4).
 */
class RoutingCommandsTest {

    @Test
    fun `routing-add creates a rule visible via list`() {
        val administration = RoutingRuleAdministration()
        val dispatcher = CliDispatcher(RoutingCommands(administration).commands())

        val output = dispatcher.dispatch(arrayOf("routing-add", "rule-1", "100", "email", "email-primary", "AT_LEAST_ONCE"))

        assertEquals("OK", output)
        assertEquals(1, administration.list().size)
    }

    @Test
    fun `routing-remove drops the rule`() {
        val administration = RoutingRuleAdministration()
        val dispatcher = CliDispatcher(RoutingCommands(administration).commands())
        dispatcher.dispatch(arrayOf("routing-add", "rule-1", "100", "email", "email-primary", "AT_LEAST_ONCE"))

        dispatcher.dispatch(arrayOf("routing-remove", "rule-1"))

        assertTrue(administration.list().isEmpty())
    }

    @Test
    fun `routing-simulate matches an existing rule without modifying it`() {
        val administration = RoutingRuleAdministration()
        val dispatcher = CliDispatcher(RoutingCommands(administration).commands())
        dispatcher.dispatch(arrayOf("routing-add", "rule-1", "100", "email", "email-primary", "AT_LEAST_ONCE"))

        val output = dispatcher.dispatch(arrayOf("routing-simulate", "sms", "email"))

        assertTrue(output.contains("matched"))
        assertTrue(output.contains("email-primary"))
        assertEquals(1, administration.list().size, "Symulacja nie powinna modyfikować zestawu reguł")
    }

    @Test
    fun `routing-simulate with no matching rule reports not matched`() {
        val dispatcher = CliDispatcher(RoutingCommands(RoutingRuleAdministration()).commands())

        val output = dispatcher.dispatch(arrayOf("routing-simulate", "sms", "email"))

        assertEquals("not matched", output)
    }

    @Test
    fun `routing-history reports changes in chronological order`() {
        val administration = RoutingRuleAdministration()
        val dispatcher = CliDispatcher(RoutingCommands(administration).commands())
        dispatcher.dispatch(arrayOf("routing-add", "rule-1", "100", "email", "email-primary", "AT_LEAST_ONCE"))
        dispatcher.dispatch(arrayOf("routing-remove", "rule-1"))

        val output = dispatcher.dispatch(arrayOf("routing-history"))

        val lines = output.lines()
        assertTrue(lines[0].contains("ADDED"))
        assertTrue(lines[1].contains("REMOVED"))
    }

    @Test
    fun `routing-history with no changes reports so`() {
        val dispatcher = CliDispatcher(RoutingCommands(RoutingRuleAdministration()).commands())

        val output = dispatcher.dispatch(arrayOf("routing-history"))

        assertEquals("Brak historii zmian", output)
    }
}
