package automatl.juras.domain

import automatl.juras.protocol.JuraCredentials
import automatl.juras.protocol.Temperature
import kotlinx.serialization.Serializable

/** Root persisted application state. One paired machine for now. */
@Serializable
data class AppState(
    val pairedDevice: PairedDevice? = null,
    val presets: List<BrewPreset> = emptyList(),
)

/**
 * The single paired coffee machine and its credentials.
 *
 * [label] is **this phone's** identity, sent to the machine in the `@HP:`
 * handshake and shown on the machine's confirm prompt. [machineName] is the
 * **machine's** own discovered name (e.g. "TT237W V08.27"), used for display.
 */
@Serializable
data class PairedDevice(
    val host: String,
    val label: String = "Juras",
    val setupPin: String = "",
    val token: String = "",
    val model: String = "EF1030",
    val machineName: String = "",
) {
    /** User-facing title for the machine (falls back to the model). */
    val displayName: String get() = machineName.ifBlank { model }

    /**
     * Credentials for the `@HP:` handshake, or `null` if no token is stored yet
     * (the client will then attempt anonymous pairing).
     */
    fun credentialsOrNull(): JuraCredentials? {
        if (token.isBlank()) return null
        val name = label.ifBlank { "Juras" }
        return JuraCredentials(
            setupPin = setupPin.trim(),
            deviceNameHex = JuraCredentials.deviceNameToHex(name),
            token = token.trim(),
        )
    }
}

/**
 * A user's saved brew: a configured [productCode] with chosen parameters. Ranges
 * and which fields apply come from the model's product catalogue (`:protocol`).
 */
@Serializable
data class BrewPreset(
    val id: String,
    val name: String,
    val productCode: Int,
    val strength: Int,
    val waterMl: Int,
    val temperature: Temperature,
    val milkMl: Int? = null,
)

/** Presets seeded on first run so the app is usable before the editor exists. */
object DefaultPresets {
    val list: List<BrewPreset> = listOf(
        BrewPreset("seed-espresso", "Espresso", 0x02, 8, 45, Temperature.HIGH),
        BrewPreset("seed-coffee", "Coffee", 0x03, 5, 100, Temperature.NORMAL),
        BrewPreset("seed-cappuccino", "Cappuccino", 0x04, 8, 60, Temperature.NORMAL, milkMl = 12),
    )
}
