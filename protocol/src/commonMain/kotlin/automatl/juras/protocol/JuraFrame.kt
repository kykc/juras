package automatl.juras.protocol

/**
 * Wire framing for JURA messages:
 *
 * ```
 * 0x2A | [escaped key byte] | [encrypted payload] | 0x0D 0x0A
 * ```
 *
 * The encrypted payload is the ASCII command text **plus a trailing `\r\n`**,
 * run through [JuraCipher]. The trailing `0x0D 0x0A` is a separate, literal frame
 * terminator. Five [SPECIAL] byte values are reserved as framing characters and
 * are escaped wherever they appear (in the key byte or the ciphertext) as
 * `0x1B, (B XOR 0x80)`.
 */
object JuraFrame {

    private const val START = 0x2A
    private const val ESC = 0x1B
    private const val CR = 0x0D
    private const val LF = 0x0A

    /** Reserved framing bytes that must be escaped wherever they occur. */
    val SPECIAL = setOf(0x00, 0x0A, 0x0D, 0x26, 0x1B)

    /** Build a complete wire frame for [command] using the given [key] (0..255). */
    fun encode(command: String, key: Int): ByteArray {
        val plain = (command + "\r\n").toByteArray(Charsets.US_ASCII)
        val cipher = JuraCipher.transform(key, plain)
        val out = ArrayList<Byte>(cipher.size + 6)
        out.add(START.toByte())
        appendEscaped(out, key)
        for (b in cipher) appendEscaped(out, b.toInt() and 0xFF)
        out.add(CR.toByte())
        out.add(LF.toByte())
        return out.toByteArray()
    }

    /**
     * Decode a complete wire frame (from the `0x2A` start sentinel through the
     * `0x0D 0x0A` terminator) back into the command string, with the trailing
     * `\r\n` stripped.
     */
    fun decode(frame: ByteArray): String {
        require(frame.isNotEmpty() && (frame[0].toInt() and 0xFF) == START) {
            "frame does not start with 0x2A sentinel"
        }
        var i = 1
        val key: Int
        if ((frame[i].toInt() and 0xFF) == ESC) {
            key = (frame[i + 1].toInt() and 0xFF) xor 0x80
            i += 2
        } else {
            key = frame[i].toInt() and 0xFF
            i += 1
        }
        val kH = (key shr 4) and 0xF
        val kL = key and 0xF

        val plain = ArrayList<Byte>(frame.size)
        var counter = 0
        while (i < frame.size) {
            val raw = frame[i].toInt() and 0xFF
            if (raw == CR) break // literal terminator
            val b: Int
            if (raw == ESC) {
                b = (frame[i + 1].toInt() and 0xFF) xor 0x80
                i += 2
            } else {
                b = raw
                i += 1
            }
            val hi = JuraCipher.cipherNibble((b shr 4) and 0xF, counter, kH, kL)
            val lo = JuraCipher.cipherNibble(b and 0xF, counter + 1, kH, kL)
            plain.add(((hi shl 4) or lo).toByte())
            counter += 2
        }
        return String(plain.toByteArray(), Charsets.US_ASCII).trimEnd('\r', '\n')
    }

    private fun appendEscaped(out: MutableList<Byte>, value: Int) {
        if (value in SPECIAL) {
            out.add(ESC.toByte())
            out.add((value xor 0x80).toByte())
        } else {
            out.add(value.toByte())
        }
    }
}
