package automatl.juras.protocol

import automatl.juras.protocol.product.Product
import automatl.juras.protocol.product.ProductKind
import automatl.juras.protocol.product.Range
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── JSON schema for machine catalogs ─────────────────────────────────────────
//
// Catalog files live in shared/src/commonMain/composeResources/files/catalogs/.
// One file per model, named "<modelId>.json". Loaded at startup by CatalogLoader.
//
// Map keys (alertBits, brewStateNames) accept either decimal ("33") or
// "0x"-prefixed hex ("0x21") strings, so contributors can use whichever
// form matches the protocol docs they're working from.

@Serializable
private data class RangeDto(
    val default: Int,
    val min: Int,
    val max: Int,
    val step: Int,
)

@Serializable
private data class ProductDto(
    val code: Int,
    val name: String,
    val kind: String,
    val strength: RangeDto? = null,
    val water: RangeDto? = null,
    val milk: RangeDto? = null,
    val bypass: RangeDto? = null,
    val defaultTemperature: String? = null,
)

@Serializable
private data class MachineCatalogDto(
    val modelId: String,
    val products: List<ProductDto>,
    /** Bit index (decimal or "0x"-hex string) → human label. */
    val alertBits: Map<String, String>,
    /** State byte (decimal or "0x"-hex string) → human label. */
    val brewStateNames: Map<String, String>,
    /** State bytes that indicate active dispensing (decimal or "0x"-hex strings). */
    val dispensingStates: List<String>,
    val maintenanceStatusFields: List<String>,
    val maintenanceCounterFields: List<String>,
)

private val json = Json { ignoreUnknownKeys = true }

private fun String.parseKey(): Int =
    if (startsWith("0x") || startsWith("0X")) drop(2).toInt(16) else toInt()

private fun RangeDto.toRange() = Range(default = default, min = min, max = max, step = step)

private fun ProductDto.toProduct() = Product(
    code = code,
    name = name,
    kind = ProductKind.valueOf(kind),
    strength = strength?.toRange(),
    water = water?.toRange(),
    milk = milk?.toRange(),
    bypass = bypass?.toRange(),
    defaultTemperature = defaultTemperature?.let { Temperature.valueOf(it) },
)

fun MachineCatalog.Companion.fromJson(jsonString: String): MachineCatalog {
    val dto = json.decodeFromString<MachineCatalogDto>(jsonString)
    return MachineCatalog(
        modelId = dto.modelId,
        products = dto.products.map { it.toProduct() },
        alertBits = dto.alertBits.entries.associate { it.key.parseKey() to it.value },
        brewStateNames = dto.brewStateNames.entries.associate { it.key.parseKey() to it.value },
        dispensingStates = dto.dispensingStates.map { it.parseKey() }.toSet(),
        maintenanceStatusFields = dto.maintenanceStatusFields,
        maintenanceCounterFields = dto.maintenanceCounterFields,
    )
}
