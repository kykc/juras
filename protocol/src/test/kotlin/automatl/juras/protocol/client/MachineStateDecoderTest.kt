package automatl.juras.protocol.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineStateDecoderTest {

    private fun MachineState.bits() = activeAlerts.map { it.bit }.toSet()

    /** Real `@TF:` capture, water tank in place, machine ready to brew. */
    @Test
    fun `ready state decodes coffee ready without fill water`() {
        val state = MachineStateDecoder.decode("000400000C0000")
        assertTrue("coffee ready (bit 13) set", 13 in state.bits())
        assertTrue("fill water (bit 1) clear", 1 !in state.bits())
        assertEquals("Coffee ready", state.activeAlerts.first { it.bit == 13 }.name)
    }

    /** Real `@TF:` capture, water tank pulled out ("fill water" on the display). */
    @Test
    fun `tank-out state decodes fill water without coffee ready`() {
        val state = MachineStateDecoder.decode("400000000C0000")
        assertTrue("fill water (bit 1) set", 1 in state.bits())
        assertTrue("coffee ready (bit 13) clear", 13 !in state.bits())
        assertEquals("Fill water", state.activeAlerts.first { it.bit == 1 }.name)
    }

    /** Background status bits (byte 4 = 0x0C) are present in both captures. */
    @Test
    fun `steady background flags are decoded`() {
        val state = MachineStateDecoder.decode("000400000C0000")
        assertTrue("energy save (bit 36)", 36 in state.bits())
        assertTrue("active RF filter (bit 37)", 37 in state.bits())
    }

    @Test
    fun `payload too short does not throw and decodes what it can`() {
        val state = MachineStateDecoder.decode("40")
        assertTrue(1 in state.bits()) // byte 0 only
        assertEquals("40", state.rawHex)
    }
}
