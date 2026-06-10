package automatl.juras.protocol.client

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

    fun decode(payloadHex: String): MachineState {
        val hex = payloadHex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val active = ALERT_BITS.entries
            .filter { (bit, _) ->
                val byteIndex = bit / 8
                byteIndex < bytes.size &&
                    (bytes[byteIndex].toInt() and (0x80 ushr (bit % 8))) != 0
            }
            .map { (bit, name) -> AlertFlag(bit, name) }
            .sortedBy { it.bit }
        return MachineState(payloadHex, active)
    }

    val ALERT_BITS: Map<Int, String> = mapOf(
        0 to "Insert tray",
        1 to "Fill water",
        2 to "Empty grounds",
        3 to "Empty tray",
        4 to "Insert coffee bin",
        5 to "Outlet missing",
        6 to "Rear cover missing",
        7 to "Milk alert",
        8 to "Fill system",
        9 to "System filling",
        10 to "No beans",
        11 to "Welcome",
        12 to "Heating up",
        13 to "Coffee ready",
        14 to "No milk (sensor)",
        15 to "Milk sensor error",
        16 to "No signal (milk sensor)",
        17 to "Please wait",
        18 to "Coffee rinsing",
        19 to "Ventilation closed",
        20 to "Close powder cover",
        21 to "Fill powder",
        22 to "System emptying",
        23 to "Not enough powder",
        24 to "Remove water tank",
        25 to "Press rinse",
        26 to "Goodbye",
        27 to "Periphery alert",
        28 to "Powder product",
        29 to "Program-mode status",
        30 to "Error status",
        31 to "Enjoy product",
        32 to "Filter alert",
        33 to "Descaling alert",
        34 to "Cleaning alert",
        35 to "Cappu rinse alert",
        36 to "Energy save",
        37 to "Active RF filter",
        38 to "Remote screen",
        39 to "Locked keys",
        40 to "Close tap",
        41 to "Cappu clean alert",
        42 to "Info: cappu clean alert",
        43 to "Info: coffee clean alert",
        44 to "Info: descale alert",
        45 to "Info: filter used up",
        46 to "Steam ready",
        47 to "Switch-off delay active",
        48 to "Close front cover",
        49 to "Left bean alert",
        50 to "Right bean alert",
        53 to "Empty grounds (RTC)",
        54 to "ML/OZ status",
    )
}
