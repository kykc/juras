package automatl.juras.protocol.product

import automatl.juras.protocol.Temperature

/** Whether a product involves coffee, milk, both, or just water. */
enum class ProductKind { COFFEE, MILK, COFFEE_MILK, WATER }

/**
 * A brewable product definition — the per-model reference data that bounds a brew
 * (valid ranges, defaults, which parameters apply). Mirrors the `<PRODUCT>`
 * entries in the machine XML and `PRODUCTS` in `../jura.py`.
 *
 * This is **reference data**, not user state: a saved preset references a product
 * by [code] and stores the chosen values.
 */
data class Product(
    val code: Int,
    val name: String,
    val kind: ProductKind,
    val defaultStrength: Int,
    val strengthMin: Int,
    val strengthMax: Int,
    val defaultWaterMl: Int,
    val waterMinMl: Int,
    val waterMaxMl: Int,
    val waterStepMl: Int,
    val defaultTemperature: Temperature,
    val defaultMilkMl: Int? = null,
) {
    /** Coffee-bearing products accept a strength setting. */
    val hasStrength: Boolean get() = kind == ProductKind.COFFEE || kind == ProductKind.COFFEE_MILK

    /** Milk products accept a milk-foam amount. */
    val hasMilk: Boolean get() = kind == ProductKind.MILK || kind == ProductKind.COFFEE_MILK
}
