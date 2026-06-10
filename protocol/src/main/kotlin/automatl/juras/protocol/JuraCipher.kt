package automatl.juras.protocol

/**
 * The JURA WiFi Smart Connect v2 nibble cipher.
 *
 * It is **symmetric**: [transform] is its own inverse, so the same routine both
 * encrypts and decrypts. Each byte is processed as two nibbles; a `counter`
 * starts at 0 and increments by 2 per byte. The key contributes its high and low
 * nibble (`kH`, `kL`).
 *
 * Derived from reverse engineering the JURA Smart Connect v2 protocol; see
 * `CLAUDE.md` §5 and `JuraCipherTest` for golden vectors.
 */
object JuraCipher {

    val LUT_A = intArrayOf(1, 0, 3, 2, 15, 14, 8, 10, 6, 13, 7, 12, 11, 9, 5, 4)
    val LUT_B = intArrayOf(9, 12, 6, 11, 10, 15, 2, 14, 13, 0, 4, 3, 1, 8, 7, 5)

    /** Python's `% 256 % 16` reduces to a non-negative mod-16 (256 is a multiple of 16). */
    private fun mod16(x: Int): Int = ((x % 16) + 16) % 16

    fun cipherNibble(nibble: Int, counter: Int, kH: Int, kL: Int): Int {
        val iB = mod16(nibble + counter + kH)
        val i11 = counter shr 4
        val idx1 = mod16(i11 + LUT_A[iB] + kL - counter - kH)
        val idx2 = mod16(LUT_B[idx1] + kH + counter - kL - i11)
        return mod16(LUT_A[idx2] - counter - kH)
    }

    /**
     * Encrypt or decrypt [data] under [key] (0..255). Symmetric — applying this
     * twice with the same key returns the original bytes. No framing/escaping is
     * applied here; this operates purely on the raw payload bytes.
     */
    fun transform(key: Int, data: ByteArray): ByteArray {
        val kH = (key shr 4) and 0xF
        val kL = key and 0xF
        val out = ByteArray(data.size)
        var counter = 0
        for (i in data.indices) {
            val b = data[i].toInt() and 0xFF
            val hi = cipherNibble((b shr 4) and 0xF, counter, kH, kL)
            val lo = cipherNibble(b and 0xF, counter + 1, kH, kL)
            out[i] = ((hi shl 4) or lo).toByte()
            counter += 2
        }
        return out
    }
}
