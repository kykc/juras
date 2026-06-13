package automatl.juras.protocol.client

/**
 * Decodes a `@TV:` brew-progress payload into a [BrewProgress]. The same 16-byte
 * payload is delivered both in the TCP `@TV` push and — while a product is running —
 * in the UDP status reply ([automatl.juras.protocol.transport.UdpStatusReply]). Mirrors
 * `decode_tv` in `../jura.py`.
 */
object BrewProgressDecoder {

    fun decode(dataHex: String): BrewProgress {
        val clean = dataHex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        val hex = (clean + "F".repeat(32)).substring(0, 32)
        val b = IntArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
        val state = b[0]
        return if (state in BrewStateNames.DISPENSING_STATES) {
            BrewProgress.Dispensing(doneMl = b[4] * 5, totalMl = b[5] * 5, percent = minOf(b[14], 100))
        } else {
            BrewProgress.Phase(state, BrewStateNames.nameFor(state) ?: "State 0x%02X".format(state))
        }
    }
}
