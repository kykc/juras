package automatl.juras.protocol.transport

/**
 * Parsed JURA **UDP** status reply. Field offsets verified against packet captures:
 *
 * ```
 * [0:2]    total length (big-endian u16)
 * [2:4]    marker; (marker & 0x0FFF) == 0x05F3, bit15 set, bit14 clear
 * [4:20]   machine name   (ASCII, space/NUL padded)
 * [20:52]  machine id      (ASCII)
 * [52:68]  string
 * [68:78]  version/counter words
 * [109]    flags; bit0 == 0 ⇒ a product is running (→ @TV), else idle (→ @TF)
 * [110:len] status payload bytes
 * ```
 *
 * J.O.E. rebuilds `"@T" + ("V"|"F") + ":" + hex(payload) + "\r\n"` from this and runs
 * it through the *same* `@TV`/`@TF` parser as the TCP channel — so [statusFrame]/
 * [payloadHex] feed straight into our existing decoders (e.g. `MachineStateDecoder`).
 *
 * Parsing is **lenient**: it never throws on a short or unexpected packet, so an
 * unrecognised reply still surfaces [rawHex] for inspection. Check [markerOk] and
 * [length] before trusting the structured fields.
 */
data class UdpStatusReply(
    /** Whole reply as uppercase hex (for captures / debugging). */
    val rawHex: String,
    /** Reply length in bytes. */
    val length: Int,
    /** Whether the `0x05F3` marker matched — i.e. this looks like a JURA status reply. */
    val markerOk: Boolean,
    val name: String,
    val machineId: String,
    /** True if a product is currently being made (`@TV`), false if idle (`@TF`). */
    val productRunning: Boolean,
    /** Status payload (bytes after offset 110) as uppercase hex. */
    val payloadHex: String,
    /** TCP-equivalent frame: `@TV:<hex>` (running) or `@TF:<hex>` (idle). */
    val statusFrame: String,
) {
    companion object {
        const val PAYLOAD_OFFSET = 110
        private const val MARKER_VALUE = 0x05F3

        fun parse(data: ByteArray): UdpStatusReply {
            val rawHex = data.toHex()
            val length = data.size
            val markerOk = length >= 4 && (u16(data, 2) and 0x0FFF) == MARKER_VALUE

            if (length < PAYLOAD_OFFSET) {
                return UdpStatusReply(rawHex, length, markerOk, "", "", false, "", "")
            }

            val totalLen = u16(data, 0)
            val name = ascii(data, 4, 20)
            val machineId = ascii(data, 20, 52)
            val flags = data[109].toInt() and 0xFF
            val productRunning = (flags and 0x01) == 0
            val end = totalLen.coerceIn(PAYLOAD_OFFSET, length)
            val payloadHex = data.copyOfRange(PAYLOAD_OFFSET, end).toHex()
            val tag = if (productRunning) "V" else "F"
            return UdpStatusReply(
                rawHex = rawHex,
                length = length,
                markerOk = markerOk,
                name = name,
                machineId = machineId,
                productRunning = productRunning,
                payloadHex = payloadHex,
                statusFrame = "@T$tag:$payloadHex\r\n",
            )
        }

        private fun u16(d: ByteArray, off: Int): Int =
            ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)

        private fun ascii(d: ByteArray, from: Int, to: Int): String =
            String(d.copyOfRange(from, to.coerceAtMost(d.size)), Charsets.ISO_8859_1).trim { it <= ' ' }

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }
}
