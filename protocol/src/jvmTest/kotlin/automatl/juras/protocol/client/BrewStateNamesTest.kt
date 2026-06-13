package automatl.juras.protocol.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewStateNamesTest {

    @Test
    fun `known intake states resolve to captions`() {
        assertEquals("Heating up", BrewStateNames.nameFor(0x21))
        assertEquals("Fill water tank", BrewStateNames.nameFor(0x40))
        assertEquals("Coffee ready", BrewStateNames.nameFor(0x24)) // jura.py mislabeled this
        assertEquals("Enjoy", BrewStateNames.nameFor(0x3E))
    }

    @Test
    fun `dispensing states are flagged and not captions`() {
        // Each product's ProgressAdjust value (EF1030).
        for (code in listOf(0x34, 0x37, 0x3C, 0x41)) {
            assertTrue("0x%02X dispensing".format(code), code in BrewStateNames.DISPENSING_STATES)
            assertNull("0x%02X has no caption".format(code), BrewStateNames.nameFor(code))
        }
    }

    @Test
    fun `unknown code has no caption`() {
        assertNull(BrewStateNames.nameFor(0x7F))
        assertFalse(0x7F in BrewStateNames.DISPENSING_STATES)
    }
}
