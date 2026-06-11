package automatl.juras.protocol.client

import automatl.juras.protocol.JuraCredentials
import automatl.juras.protocol.transport.JuraConnection
import java.net.SocketTimeoutException

/**
 * High-level operations over an open [JuraConnection].
 *
 * Stateless aside from the connection it borrows. Reads are request/response;
 * [brew] streams progress until completion. Parsing mirrors the reference client
 * (`../jura.py`) and the protocol reference (`CLAUDE.md` §5).
 */
class JuraClient(private val conn: JuraConnection) {

    /** Send the `@HP:` handshake. Pass `null` to initiate pairing (`@HP:,,`). */
    fun authenticate(credentials: JuraCredentials?): AuthResult {
        conn.send(credentials?.hpCommand() ?: JuraCredentials.PAIRING_COMMAND)
        val resp = conn.receive()
        return when {
            resp.startsWith("@hp4:") -> AuthResult.PinRequired(resp.substring(5))
            resp.startsWith("@hp4") -> AuthResult.Authenticated
            else -> AuthResult.Rejected(resp)
        }
    }

    /**
     * Start a product and stream progress until it finishes. Wraps the brew in a
     * Remote Screen session (`@TS:01` … `@TS:00`) and dispatches `@tb`/`@tv:`
     * frames to [onProgress]. Blocking — run off the main thread. Use a connection
     * with a long read timeout (brewing has gaps between frames).
     *
     * [payload] is the 32-hex `@TP:` body from [TpPayload]. To stop a brew in
     * progress, send `@TG:FF` on the same connection from another thread.
     */
    fun brew(payload: String, onProgress: (BrewProgress) -> Unit): BrewOutcome {
        conn.send("@TS:01")
        conn.receive()
        try {
            conn.send("@TP:$payload")
            while (true) {
                val resp = try {
                    conn.receive()
                } catch (_: SocketTimeoutException) {
                    return BrewOutcome(completed = true, statusByte = null)
                }
                val lower = resp.lowercase()
                when {
                    lower.startsWith("@tb") -> onProgress(BrewProgress.Started)
                    lower.startsWith("@tv:") -> onProgress(decodeBrewState(resp.substring(4)))
                    lower.startsWith("@tf:") -> {
                        val status = resp.substring(4).take(2).toIntOrNull(16)
                        return BrewOutcome(completed = status == 0, statusByte = status)
                    }
                    lower.startsWith("@tp:00") -> return BrewOutcome(completed = true, statusByte = 0)
                    // @tp echo, @ts session signals: ignore and keep reading.
                }
            }
        } finally {
            runCatching {
                conn.send("@TS:00")
                conn.receive()
            }
        }
    }

    /** Decode a `@tv:` progress payload. Mirrors `decode_tv` in `../jura.py`. */
    private fun decodeBrewState(dataHex: String): BrewProgress {
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

    /** Read everything that is safe and idempotent. */
    fun readReport(): MachineReport {
        // Statistics are simple request/response and are wrapped in a session.
        val report = withSession {
            MachineReport(
                productCounts = readProductCounters(),
                maintenanceStatus = readMaintenanceStatus(),
                maintenanceCounters = readMaintenanceCounters(),
                machineState = null,
            )
        }
        // Machine state is a *streaming* subscription (@TM:50 → @TF: pushes), so
        // it is read last and outside the session: once subscribed, the machine
        // keeps pushing frames until disconnect, which would otherwise pollute
        // subsequent request/response reads.
        return report.copy(machineState = runCatching { readMachineState() }.getOrNull())
    }

    /** Wrap [block] in a Remote Screen session (`@TS:01` … `@TS:00`). */
    private fun <T> withSession(block: () -> T): T {
        conn.send("@TS:01")
        conn.receive()
        try {
            return block()
        } finally {
            runCatching {
                conn.send("@TS:00")
                conn.receive()
            }
        }
    }

    // ── product counters (@TR:32) ────────────────────────────────────────────

    private fun readProductCounters(): List<ProductCount> {
        val combined = StringBuilder(256)
        for (page in 0 until 16) {
            conn.send("@TR:32,%02X".format(page))
            val resp = conn.receive()
            val parts = resp.split(",", limit = 3)
            val raw = if (parts.size >= 3) parts[2].trim() else ""
            combined.append((raw + "FFFF".repeat(4)).substring(0, 16))
        }
        val data = combined.toString()
        val out = ArrayList<ProductCount>()
        for ((code, name) in STAT_SLOTS) {
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
        conn.send("@TG:C0")
        val resp = conn.receive()
        if (!resp.lowercase().startsWith("@tg:c0")) return emptyList()
        val data = resp.substring(6)
        val out = ArrayList<MaintenanceStatus>()
        for ((i, name) in C0_FIELDS.withIndex()) {
            if (i * 2 + 2 > data.length) break
            val value = data.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: continue
            if (value == 0xFF) out.add(MaintenanceStatus(name, null, notApplicable = true))
            else out.add(MaintenanceStatus(name, value, notApplicable = false))
        }
        return out
    }

    // ── maintenance counters (@TG:43) ────────────────────────────────────────

    private fun readMaintenanceCounters(): List<MaintenanceCounter> {
        conn.send("@TG:43")
        val resp = conn.receive()
        if (!resp.lowercase().startsWith("@tg:43")) return emptyList()
        val data = resp.substring(6)
        val out = ArrayList<MaintenanceCounter>()
        for ((i, name) in TG43_FIELDS.withIndex()) {
            if (i * 4 + 4 > data.length) break
            val value = data.substring(i * 4, i * 4 + 4).toIntOrNull(16) ?: continue
            out.add(MaintenanceCounter(name, value))
        }
        return out
    }

    // ── machine state (@TM:50 → @TF:) ────────────────────────────────────────

    // @TM:50 subscribes to status; the machine acks (e.g. `@tm:D0`) and then
    // pushes `@TF:<14 hex>` frames periodically until disconnect. Each push is a
    // 7-byte (56-bit) alert word. Bit numbering is per-byte, MSB-first: alert bit
    // N is byte[N/8], mask 0x80 >> (N % 8). Confirmed on EF1030 hardware (tank out
    // → bit 1 "fill water"; ready → bit 13 "coffee ready").
    private fun readMachineState(): MachineState {
        conn.send("@TM:50")
        repeat(MAX_STATE_FRAMES) {
            val resp = conn.receive()
            if (resp.lowercase().startsWith("@tf:")) {
                return MachineStateDecoder.decode(resp.substring(4).trim())
            }
        }
        return MachineState("", emptyList())
    }

    companion object {
        // Product code -> display name (EF1030 / Jura E6). See CLAUDE.md §5.
        private val STAT_SLOTS = listOf(
            0x00 to "Total",
            0x02 to "Espresso",
            0x03 to "Coffee",
            0x04 to "Cappuccino",
            0x06 to "Espresso Macchiato",
            0x08 to "Milk Foam",
            0x0D to "Hot Water",
            0x0F to "Powder Product",
            0x28 to "Café Barista",
            0x29 to "Barista Lungo",
            0x31 to "2 Espressi",
            0x36 to "2 Coffee",
        )

        // Positional ordering per the machine XML / MaintenanceStatisticsParser.
        private val C0_FIELDS = listOf("Cleaning", "Filter change", "Descaling")
        private val TG43_FIELDS = listOf(
            "Cleaning", "Filter change", "Descaling",
            "Cappu rinse", "Coffee rinse", "Cappu clean",
        )

        /** Max status frames to read before giving up waiting for the first @TF: push. */
        private const val MAX_STATE_FRAMES = 8
    }
}
