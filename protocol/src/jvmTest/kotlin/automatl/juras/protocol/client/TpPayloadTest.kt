package automatl.juras.protocol.client

import automatl.juras.protocol.Temperature
import org.junit.Assert.assertEquals
import org.junit.Test

class TpPayloadTest {

    @Test
    fun `espresso 60ml strength 8 high matches protocol reference example`() {
        // Protocol reference §6 worked example.
        val payload = TpPayload.build(
            productCode = 0x02,
            strength = 8,
            waterMl = 60,
            temperature = Temperature.HIGH,
        )
        assertEquals("0200080C000002000100000000000000", payload)
    }

    @Test
    fun `payload is always 32 hex chars with F9 set`() {
        val payload = TpPayload.build(productCode = 0x03)
        assertEquals(32, payload.length)
        // byte 8 (chars 16-17) is F9 = 01
        assertEquals("01", payload.substring(16, 18))
        assertEquals("03", payload.substring(0, 2))
    }

    @Test
    fun `water and bypass are encoded as ml divided by 5`() {
        val payload = TpPayload.build(
            productCode = 0x28, // Caffé Barista
            strength = 6,
            waterMl = 60,
            temperature = Temperature.NORMAL,
            bypassMl = 40,
        )
        // byte 3 = water 60/5 = 0x0C; byte 9 = bypass 40/5 = 0x08
        assertEquals("0C", payload.substring(6, 8))
        assertEquals("08", payload.substring(18, 20))
    }

    @Test
    fun `milk foam is encoded raw`() {
        val payload = TpPayload.build(productCode = 0x04, strength = 8, waterMl = 60, milkMl = 12)
        // byte 5 = milk 12 = 0x0C (raw, not divided)
        assertEquals("0C", payload.substring(10, 12))
    }
}
