package automatl.juras.protocol.client

/** Result of the `@HP:` authentication handshake. */
sealed interface AuthResult {
    /** Credentials accepted (`@hp4`, no colon). Connection stays open. */
    data object Authenticated : AuthResult

    /** PIN challenge (`@hp4:TOKEN`): device not yet registered. Pairing required. */
    data class PinRequired(val token: String) : AuthResult

    /** Unknown/anonymous device (`@hp5:...`) or any unexpected response. */
    data class Rejected(val response: String) : AuthResult
}

/** A single product brew counter (from `@TR:32`). */
data class ProductCount(val code: Int, val name: String, val count: Int)

/** Maintenance status (`@TG:C0`): percentage toward needing service. */
data class MaintenanceStatus(val name: String, val percent: Int?, val notApplicable: Boolean)

/** Maintenance counter (`@TG:43`): cycles since last service. */
data class MaintenanceCounter(val name: String, val cycles: Int)

/** One active alert bit decoded from the `@TM:50` machine-state bitmask. */
data class AlertFlag(val bit: Int, val name: String)

/** Decoded machine state (`@TM:50`). [rawHex] is kept for diagnostics. */
data class MachineState(val rawHex: String, val activeAlerts: List<AlertFlag>)

/** Aggregated read of everything safe to query in one session. */
data class MachineReport(
    val productCounts: List<ProductCount>,
    val maintenanceStatus: List<MaintenanceStatus>,
    val maintenanceCounters: List<MaintenanceCounter>,
    val machineState: MachineState?,
)
