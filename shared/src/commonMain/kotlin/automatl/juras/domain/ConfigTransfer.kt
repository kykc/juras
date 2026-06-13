package automatl.juras.domain

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable

/**
 * Versioned envelope for exporting/importing the app configuration (paired device
 * + presets) as a human-readable YAML document. The [format]/[version] fields are
 * **required** (no defaults), so a non-Juras document fails to decode — that's the
 * import validation hook.
 *
 * Note: this includes the machine's pairing token and setup PIN, so an exported
 * file is sensitive — keep it private.
 */
@Serializable
data class ExportedConfig(
    val format: String,
    val version: Int,
    val pairedDevice: PairedDevice? = null,
    val presets: List<BrewPreset> = emptyList(),
) {
    companion object {
        const val FORMAT = "juras-config"
        const val VERSION = 1
    }
}

/** Encodes/decodes [ExportedConfig] as YAML, with validation on decode. */
object ConfigCodec {

    private val yaml = Yaml(
        configuration = YamlConfiguration(encodeDefaults = true, strictMode = false),
    )

    fun encode(state: AppState): String = yaml.encodeToString(
        ExportedConfig.serializer(),
        ExportedConfig(
            format = ExportedConfig.FORMAT,
            version = ExportedConfig.VERSION,
            pairedDevice = state.pairedDevice,
            presets = state.presets,
        ),
    )

    /** Parse and validate. Throws on malformed YAML, wrong format, or unsupported version. */
    fun decode(text: String): ExportedConfig {
        val config = yaml.decodeFromString(ExportedConfig.serializer(), text)
        require(config.format == ExportedConfig.FORMAT) { "This isn't a Juras config file." }
        require(config.version <= ExportedConfig.VERSION) {
            "This config was created by a newer version of the app (v${config.version})."
        }
        return config
    }
}
