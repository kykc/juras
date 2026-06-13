package automatl.juras.protocol.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpStatusReplyTest {

    @Test
    fun `poll packet is magic + machine ip + 8 zero bytes`() {
        // 192.168.33.236
        val ip = byteArrayOf(0xC0.toByte(), 0xA8.toByte(), 0x21, 0xEC.toByte())
        val packet = JuraUdpStatusClient.buildPollPacket(ip)
        assertEquals(16, packet.size)
        assertEquals(
            "0010A5F3C0A821EC0000000000000000",
            packet.joinToString("") { "%02X".format(it.toInt() and 0xFF) },
        )
    }

    @Test
    fun `idle reply rebuilds an @TF frame and exposes identity`() {
        val payload = byteArrayOf(0x00, 0x04, 0x00, 0x00, 0x0C, 0x00, 0x00) // "coffee ready"
        val reply = UdpStatusReply.parse(buildReply(name = "EF1030", id = "TT237W-1", flags = 0x01, payload = payload))

        assertTrue(reply.markerOk)
        assertEquals("EF1030", reply.name)
        assertEquals("TT237W-1", reply.machineId)
        assertFalse("bit0 set ⇒ idle", reply.productRunning)
        assertEquals("000400000C0000", reply.payloadHex)
        assertEquals("@TF:000400000C0000\r\n", reply.statusFrame)
    }

    @Test
    fun `product-running flag yields an @TV frame`() {
        val reply = UdpStatusReply.parse(buildReply(flags = 0x00, payload = byteArrayOf(0x12, 0x34)))
        assertTrue(reply.productRunning)
        assertEquals("@TV:1234\r\n", reply.statusFrame)
    }

    @Test
    fun `short or foreign packet does not throw and flags marker mismatch`() {
        val reply = UdpStatusReply.parse(byteArrayOf(0x00, 0x10, 0x00, 0x00, 0x42))
        assertFalse(reply.markerOk)
        assertEquals(5, reply.length)
        assertEquals("0010000042", reply.rawHex)
    }

    /** Build a synthetic reply: header + name(16) + id(32) + filler to byte 109 + flags + payload. */
    private fun buildReply(
        name: String = "EF1030",
        id: String = "TT237W-1",
        flags: Int = 0x01,
        payload: ByteArray = ByteArray(0),
    ): ByteArray {
        val total = UdpStatusReply.PAYLOAD_OFFSET + payload.size
        val out = ByteArray(total)
        out[0] = ((total ushr 8) and 0xFF).toByte()
        out[1] = (total and 0xFF).toByte()
        out[2] = 0xA5.toByte() // marker 0xA5F3: low 12 bits = 0x5F3, bit15 set, bit14 clear
        out[3] = 0xF3.toByte()
        name.toByteArray(Charsets.US_ASCII).copyInto(out, 4)
        id.toByteArray(Charsets.US_ASCII).copyInto(out, 20)
        out[109] = (flags and 0xFF).toByte()
        payload.copyInto(out, UdpStatusReply.PAYLOAD_OFFSET)
        return out
    }
}
