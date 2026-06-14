package automatl.juras.protocol.client

import automatl.juras.protocol.JuraCredentials
import automatl.juras.protocol.MachineCatalog
import automatl.juras.protocol.transport.JuraConnection
import java.io.IOException

/**
 * High-level operations over an open [JuraConnection].
 *
 * Stateless aside from the connection and [catalog] it holds. Reads are
 * request/response; [startProduct] is fire-and-forget. Parsing mirrors the
 * reference client (`../jura.py`) and the protocol reference (`CLAUDE.md` §5).
 */
class JuraClient(private val conn: JuraConnection, private val catalog: MachineCatalog) {

    /**
     * Send the `@HP:` handshake. Pass `null` to initiate pairing (`@HP:,,`).
     *
     * Skips any unsolicited/stale frames that precede the `@hp` reply so a leftover
     * frame from a prior connection can't be mistaken for the auth result.
     */
    fun authenticate(credentials: JuraCredentials?): AuthResult {
        conn.send(credentials?.hpCommand() ?: JuraCredentials.PAIRING_COMMAND)
        repeat(MAX_SKIP_FRAMES) {
            val resp = conn.receive()
            when {
                resp.startsWith("@hp4:") -> return AuthResult.PinRequired(resp.substring(5))
                resp.startsWith("@hp4") -> return AuthResult.Authenticated
                resp.startsWith("@hp") -> return AuthResult.Rejected(resp) // @hp5 / other @hp*
                // else: unsolicited frame (@tf/@tv/@ts/…), skip and keep reading.
            }
        }
        return AuthResult.Rejected("no @hp response")
    }

    /**
     * Start a product (`@TP`). **Fire-and-forget:** the machine brews autonomously and
     * keeps going regardless of this TCP connection, so we neither open a Remote Screen
     * session (`@TS:01`) nor read a reply — progress is observed out-of-band over UDP
     * (see [automatl.juras.protocol.transport.JuraUdpStatusClient]). The caller can close
     * the connection immediately after. [payload] is the 32-hex `@TP:` body from
     * [TpPayload].
     */
    fun startProduct(payload: String) {
        conn.send("@TP:$payload")
    }

    /** Cancel the current product (`@TG:FF`). Fire-and-forget, like [startProduct]. */
    fun cancelProduct() {
        conn.send("@TG:FF")
    }

    /** Read the statistics that are safe and idempotent (counters + maintenance). */
    fun readReport(): MachineReport {
        // Plain request/response reads — **no** Remote Screen session (`@TS:01`). J.O.E.
        // issues each of these (`@TR`/`@TG`) as a standalone command with no session, so
        // we don't need one either. Dropping it avoids locking the keypad and, crucially,
        // the `@TF` push frames a session would interleave — those made `request()`
        // skip-loop and occasionally hang. Each sub-read is guarded so one hiccup doesn't
        // drop the rest. Live machine flags come from UDP, not here.
        return MachineReport(
            productCounts = runCatching { readProductCounters() }.getOrDefault(emptyList()),
            maintenanceStatus = runCatching { readMaintenanceStatus() }.getOrDefault(emptyList()),
            maintenanceCounters = runCatching { readMaintenanceCounters() }.getOrDefault(emptyList()),
        )
    }

    /**
     * Send [command] and return the first response frame whose lowercased text
     * starts with [expectedPrefix].
     *
     * The machine interleaves **unsolicited** frames (it spontaneously emits `@TS`
     * and `@TF:` status frames after auth and on state changes); reading "exactly
     * one frame" per command therefore desyncs request/response and shifts the
     * results. Skipping to the matching echo prefix tolerates those, and recovers
     * from any leftover frames a previous read left behind.
     */
    private fun request(command: String, expectedPrefix: String): String {
        conn.send(command)
        val want = expectedPrefix.lowercase()
        repeat(MAX_SKIP_FRAMES) {
            val resp = conn.receive()
            if (resp.lowercase().startsWith(want)) return resp
        }
        throw IOException("no '$expectedPrefix' response from machine")
    }

    // ── product counters (@TR:32) ────────────────────────────────────────────

    private fun readProductCounters(): List<ProductCount> {
        val statSlots = listOf(0x00 to "Total") + catalog.products.map { it.code to it.name }
        val combined = StringBuilder(256)
        for (page in 0 until 16) {
            val resp = request("@TR:32,%02X".format(page), "@tr:32")
            val parts = resp.split(",", limit = 3)
            val raw = if (parts.size >= 3) parts[2].trim() else ""
            combined.append((raw + "FFFF".repeat(4)).substring(0, 16))
        }
        val data = combined.toString()
        val out = ArrayList<ProductCount>()
        for ((code, name) in statSlots) {
            val off = code * 4
            if (off + 4 <= data.length) {
                val value = data.substring(off, off + 4).toIntOrNull(16) ?: continue
                if (value != 0xFFFF) out.add(ProductCount(code, name, value))
            }
        }
        return out
    }

    // ── maintenance status (@TG:C0) ──────────────────────────────────────────

    private fun readMaintenanceStatus(): List<MaintenanceStatus> {
        val resp = request("@TG:C0", "@tg:c0")
        val data = resp.substring(6)
        val out = ArrayList<MaintenanceStatus>()
        for ((i, name) in catalog.maintenanceStatusFields.withIndex()) {
            if (i * 2 + 2 > data.length) break
            val value = data.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: continue
            if (value == 0xFF) out.add(MaintenanceStatus(name, null, notApplicable = true))
            else out.add(MaintenanceStatus(name, value, notApplicable = false))
        }
        return out
    }

    // ── maintenance counters (@TG:43) ────────────────────────────────────────

    private fun readMaintenanceCounters(): List<MaintenanceCounter> {
        val resp = request("@TG:43", "@tg:43")
        val data = resp.substring(6)
        val out = ArrayList<MaintenanceCounter>()
        for ((i, name) in catalog.maintenanceCounterFields.withIndex()) {
            if (i * 4 + 4 > data.length) break
            val value = data.substring(i * 4, i * 4 + 4).toIntOrNull(16) ?: continue
            out.add(MaintenanceCounter(name, value))
        }
        return out
    }

    companion object {
        private const val MAX_SKIP_FRAMES = 24
    }
}
