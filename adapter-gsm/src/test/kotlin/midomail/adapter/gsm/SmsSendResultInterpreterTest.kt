package midomail.adapter.gsm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SmsSendResultInterpreterTest {

    @Test
    fun `RESULT_OK is interpreted as Sent`() {
        val result = SmsSendResultInterpreter.interpret(android.app.Activity.RESULT_OK)

        assertIs<SmsSendResult.Sent>(result)
    }

    @Test
    fun `RESULT_ERROR_GENERIC_FAILURE is interpreted as Failed with a matching reason`() {
        val result = SmsSendResultInterpreter.interpret(android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE)

        assertEquals(SmsSendResult.Failed("GENERIC_FAILURE"), result)
    }

    @Test
    fun `RESULT_ERROR_NO_SERVICE is interpreted as Failed with a matching reason`() {
        val result = SmsSendResultInterpreter.interpret(android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE)

        assertEquals(SmsSendResult.Failed("NO_SERVICE"), result)
    }

    @Test
    fun `RESULT_ERROR_NULL_PDU is interpreted as Failed with a matching reason`() {
        val result = SmsSendResultInterpreter.interpret(android.telephony.SmsManager.RESULT_ERROR_NULL_PDU)

        assertEquals(SmsSendResult.Failed("NULL_PDU"), result)
    }

    @Test
    fun `RESULT_ERROR_RADIO_OFF is interpreted as Failed with a matching reason`() {
        val result = SmsSendResultInterpreter.interpret(android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF)

        assertEquals(SmsSendResult.Failed("RADIO_OFF"), result)
    }

    @Test
    fun `unknown result code is interpreted as Failed with the code embedded in the reason`() {
        val result = SmsSendResultInterpreter.interpret(9999)

        assertEquals(SmsSendResult.Failed("UNKNOWN_9999"), result)
    }
}
