package automatl.juras.protocol

import automatl.juras.protocol.product.Ef1030Catalog
import automatl.juras.protocol.product.Product

/**
 * Per-model data bundle: everything that varies between JURA machine types.
 * The protocol (cipher, framing, auth, commands) is identical across all models;
 * only the data in this class differs. Obtain via [forModel].
 */
data class MachineCatalog(
    val modelId: String,
    val products: List<Product>,
    /** Alert bit index → human label, for decoding `@TF:` status payloads. */
    val alertBits: Map<Int, String>,
    /** Progress state byte → human label, for `@TV:` brew-progress frames. */
    val brewStateNames: Map<Int, String>,
    /** State bytes that indicate active dispensing (show water progress, not a caption). */
    val dispensingStates: Set<Int>,
    /** Field names for the `@TG:C0` maintenance-status reply (positional). */
    val maintenanceStatusFields: List<String>,
    /** Field names for the `@TG:43` maintenance-counters reply (positional). */
    val maintenanceCounterFields: List<String>,
) {
    fun productByCode(code: Int): Product? = products.firstOrNull { it.code == code }

    companion object {
        @Volatile private var registry: Map<String, MachineCatalog> = emptyMap()

        /**
         * Register a catalog loaded from a JSON file. Overwrites any previous entry
         * for the same [MachineCatalog.modelId]. Called by `CatalogLoader` at startup.
         */
        fun register(catalog: MachineCatalog) {
            registry = registry + (catalog.modelId to catalog)
        }

        /**
         * Returns the catalog for [modelId] from the runtime registry, falling back to
         * the hardcoded [Ef1030Catalog] for EF1030 (or as a last-resort default for any
         * unknown model before JSON files have loaded).
         */
        fun forModel(modelId: String): MachineCatalog =
            registry[modelId] ?: if (modelId == "EF1030") Ef1030Catalog else registry["EF1030"] ?: Ef1030Catalog
    }
}
