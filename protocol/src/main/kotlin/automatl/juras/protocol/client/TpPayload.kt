package automatl.juras.protocol.client

import automatl.juras.protocol.Temperature

/**
 * Builds the 32-hex-char (16-byte) `@TP:` brew payload. Field→byte mapping
 * (`Fn` → byte `n-1`), ported from `build_tp_payload` in `../jura.py` and the
 * protocol reference §6:
 *
 * - byte 0: product code
 * - byte 2: F3 strength (1–10)
 * - byte 3: F4 water (ml ÷ 5)
 * - byte 5: F6 milk foam (raw)
 * - byte 6: F7 temperature (0/1/2)
 * - byte 8: F9 always `01`
 * - byte 9: F10 bypass water (ml ÷ 5) — barista products
 *
 * Pass `null` for any parameter the product doesn't support; it stays `00`.
 */
object TpPayload {

    fun build(
        productCode: Int,
        strength: Int? = null,
        waterMl: Int? = null,
        milkMl: Int? = null,
        temperature: Temperature? = null,
        bypassMl: Int? = null,
    ): String {
        val bytes = IntArray(16)
        bytes[0] = productCode and 0xFF
        strength?.let { bytes[2] = it and 0xFF }
        waterMl?.let { bytes[3] = (it / 5) and 0xFF }
        milkMl?.let { bytes[5] = it and 0xFF }
        temperature?.let { bytes[6] = it.wire and 0xFF }
        bytes[8] = 0x01
        bypassMl?.let { bytes[9] = (it / 5) and 0xFF }
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
