package automatl.juras.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JuraCipherTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    /**
     * Golden frames generated from the known-working reference client (`../jura.py`):
     *
     *   key|command|expected full wire frame (hex)
     *
     * These cover normal keys, keys that are SPECIAL (escaped), and a long
     * payload whose ciphertext contains an escaped 0x0D.
     */
    private val goldenFrames = listOf(
        Triple(0x5A, "@TG:C0", "2A5AB3809313F0CD9BA50D0A"),
        Triple(0x10, "@HP:,,", "2A10C3DF75A4668580650D0A"),
        Triple(0x00, "@TS:01", "2A1B80E72DBB93B9A443D90D0A"),
        Triple(0x1B, "@TM:50", "2A1B9B141C97DCFB9244AC0D0A"),
        Triple(0x26, "@TR:32,00", "2A1BA678E3AC1B8D8AD780A662E4E60D0A"),
        Triple(
            0x7C,
            "@HP:117711,506978656C20392050726F," +
                "05BB769602CB748C05C99A49DEA045A107A9DE00359DE2E913342BF9D5FC558E",
            "2A7C39CF36ADCEC8FFBEC8795DEB679E85FDAE62C310ACE8E46422F50309D7682" +
                "DFA196694D88CC50CB8C29EE3FC7794CE1BA65505CCC7C65E1D52D25D2CEF666" +
                "B6D6CB71CA6B1E81C0FE29DA2189F802F14EB8982A4F0AAD7EF3D94ACEC09C5E" +
                "6647443CC73760D0A",
        ),
    )

    @Test
    fun `encode matches golden wire frames from reference client`() {
        for ((key, command, expected) in goldenFrames) {
            val actual = JuraFrame.encode(command, key).toHex()
            assertEquals("command=$command key=$key", expected, actual)
        }
    }

    @Test
    fun `decode of golden frames recovers the original command`() {
        for ((_, command, frameHex) in goldenFrames) {
            assertEquals(command, JuraFrame.decode(hex(frameHex)))
        }
    }

    @Test
    fun `transform is its own inverse for all key and byte combinations`() {
        val rng = Random(1234)
        repeat(50) {
            val key = rng.nextInt(256)
            val data = ByteArray(rng.nextInt(1, 64)).also { rng.nextBytes(it) }
            val once = JuraCipher.transform(key, data)
            val twice = JuraCipher.transform(key, once)
            assertArrayEquals("key=$key", data, twice)
        }
    }

    @Test
    fun `encode then decode round-trips arbitrary commands`() {
        val rng = Random(99)
        val commands = listOf(
            "@TG:C0", "@TS:01", "@TM:50", "@HP:,,",
            "@TR:32,0F", "@TP:0200080C000002000100000000000000",
            // include payloads that will exercise SPECIAL bytes in the ciphertext
            "@XX:&&&&", "@YY:", "@ZZ:0000000000",
        )
        for (cmd in commands) {
            repeat(8) {
                val key = KeySelector.pick(rng)
                assertEquals(cmd, JuraFrame.decode(JuraFrame.encode(cmd, key)))
            }
        }
    }

    @Test
    fun `key selector never yields a forbidden low nibble`() {
        val rng = Random(7)
        repeat(10_000) {
            val k = KeySelector.pick(rng)
            assertTrue(k in 0..255)
            assertTrue("low nibble of $k", (k and 0xF) != 14 && (k and 0xF) != 15)
        }
    }
}
