package automatl.juras.protocol

/**
 * Credentials used in the `@HP:` authentication command:
 *
 * ```
 * @HP:<setupPin>,<deviceNameHex>,<token>
 * ```
 *
 * - [setupPin]: numeric machine setup PIN (from initial registration)
 * - [deviceNameHex]: device display name as hex-encoded ASCII
 * - [token]: 64-hex auth token issued by the machine during pairing (stored verbatim)
 */
data class JuraCredentials(
    val setupPin: String,
    val deviceNameHex: String,
    val token: String,
) {
    fun hpCommand(): String = "@HP:$setupPin,$deviceNameHex,$token"

    companion object {
        /** Empty-credential command used to initiate pairing. */
        const val PAIRING_COMMAND = "@HP:,,"

        fun deviceNameToHex(name: String): String =
            name.toByteArray(Charsets.US_ASCII).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }
}
