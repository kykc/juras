package automatl.juras.data

import android.content.Context
import automatl.juras.protocol.JuraCredentials

/** User-entered connection configuration. */
data class ConnectionConfig(
    val host: String = "",
    val setupPin: String = "",
    val deviceName: String = "Juras",
    val token: String = "",
) {
    /**
     * Build credentials for the `@HP:` handshake, or `null` if no token has been
     * entered yet (in which case the client will attempt anonymous pairing).
     */
    fun credentialsOrNull(): JuraCredentials? {
        if (token.isBlank()) return null
        val name = deviceName.ifBlank { "Juras" }
        return JuraCredentials(
            setupPin = setupPin.trim(),
            deviceNameHex = JuraCredentials.deviceNameToHex(name),
            token = token.trim(),
        )
    }
}

/** Persists [ConnectionConfig] in SharedPreferences (device-local only). */
class JuraSettings(context: Context) {
    private val prefs = context.getSharedPreferences("juras_settings", Context.MODE_PRIVATE)

    fun load(): ConnectionConfig = ConnectionConfig(
        host = prefs.getString(KEY_HOST, "").orEmpty(),
        setupPin = prefs.getString(KEY_PIN, "").orEmpty(),
        deviceName = prefs.getString(KEY_DEVICE, "Juras").orEmpty(),
        token = prefs.getString(KEY_TOKEN, "").orEmpty(),
    )

    fun save(config: ConnectionConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host)
            .putString(KEY_PIN, config.setupPin)
            .putString(KEY_DEVICE, config.deviceName)
            .putString(KEY_TOKEN, config.token)
            .apply()
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PIN = "setup_pin"
        const val KEY_DEVICE = "device_name"
        const val KEY_TOKEN = "token"
    }
}
