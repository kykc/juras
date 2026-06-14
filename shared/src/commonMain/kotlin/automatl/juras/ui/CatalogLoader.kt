package automatl.juras.ui

import automatl.juras.protocol.MachineCatalog
import automatl.juras.protocol.fromJson
import juras.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Loads bundled machine catalogs from composeResources/files/catalogs/ and
 * registers them with [MachineCatalog] at app startup. Until this runs the
 * hardcoded Ef1030Catalog fallback remains active, so there is no cold-start gap
 * for EF1030 users.
 *
 * To add support for a new model: drop a `<ModelId>.json` file (matching the
 * schema of EF1030.json) into the catalogs/ directory and add the model ID to
 * [BUNDLED_MODELS]. No Kotlin code changes needed.
 */
object CatalogLoader {

    private val BUNDLED_MODELS = listOf("EF1030")

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadAll() {
        for (modelId in BUNDLED_MODELS) {
            runCatching {
                val json = Res.readBytes("files/catalogs/$modelId.json").decodeToString()
                MachineCatalog.register(MachineCatalog.fromJson(json))
            }
        }
    }
}
