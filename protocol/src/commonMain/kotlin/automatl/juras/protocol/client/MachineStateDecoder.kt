package automatl.juras.protocol.client

import automatl.juras.protocol.MachineCatalog

/**
 * Decodes the JURA machine status word delivered in `@TF:<hex>` push frames
 * (subscribed via `@TM:50`).
 *
 * The payload is a 7-byte (56-bit) alert word. Bit numbering is **per-byte,
 * MSB-first**: alert bit `N` lives in `byte[N / 8]` at mask `0x80 ushr (N % 8)`.
 * Confirmed on EF1030 hardware — tank removed → bit 1 ("fill water"); ready →
 * bit 13 ("coffee ready").
 *
 * Alert bit assignments derived from protocol reverse engineering and hardware
 * verification on JURA E6 / EF1030; bits 51/52 are undefined. EF1030-specific —
 * other models define their own bits (see CLAUDE.md §5).
 */
object MachineStateDecoder {

    fun decode(payloadHex: String, catalog: MachineCatalog): MachineState {
        val hex = payloadHex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val active = catalog.alertBits.entries
            .filter { (bit, _) ->
                val byteIndex = bit / 8
                byteIndex < bytes.size &&
                    (bytes[byteIndex].toInt() and (0x80 ushr (bit % 8))) != 0
            }
            .map { (bit, name) -> AlertFlag(bit, name) }
            .sortedBy { it.bit }
        return MachineState(payloadHex, active)
    }
}
