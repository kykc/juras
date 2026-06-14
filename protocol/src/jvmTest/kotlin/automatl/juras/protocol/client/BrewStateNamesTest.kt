package automatl.juras.protocol.client

import automatl.juras.protocol.product.Ef1030Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewStateNamesTest {

    @Test
    fun `known intake states resolve to captions`() {
        assertEquals("Heating up", Ef1030Catalog.brewStateNames[0x21])
        assertEquals("Fill water tank", Ef1030Catalog.brewStateNames[0x40])
        assertEquals("Coffee ready", Ef1030Catalog.brewStateNames[0x24])
        assertEquals("Enjoy", Ef1030Catalog.brewStateNames[0x3E])
    }

    @Test
    fun `dispensing states are flagged and not captions`() {
        for (code in listOf(0x34, 0x37, 0x3C, 0x41)) {
            assertTrue("0x%02X dispensing".format(code), code in Ef1030Catalog.dispensingStates)
            assertNull("0x%02X has no caption".format(code), Ef1030Catalog.brewStateNames[code])
        }
    }

    @Test
    fun `unknown code has no caption`() {
        assertNull(Ef1030Catalog.brewStateNames[0x7F])
        assertFalse(0x7F in Ef1030Catalog.dispensingStates)
    }
}
