package automatl.juras.protocol.product

import automatl.juras.protocol.Temperature

/** Whether a product involves coffee, milk, both, or just water/tea. */
enum class ProductKind { COFFEE, MILK, COFFEE_MILK, WATER }

/** An adjustable integer parameter: factory [default], constrained to [min]..[max] in [step] increments. */
data class Range(val default: Int, val min: Int, val max: Int, val step: Int)

/**
 * A brewable product definition — per-model reference data that bounds a brew.
 * Mirrors a `<PRODUCT>` entry in the machine XML. Each adjustable parameter is
 * present only if the product actually supports it (a `null` [Range]/temperature
 * means "not adjustable for this product"), so e.g. 2 Espressi has no strength and
 * Milk Foam has only a milk amount.
 *
 * Parameters map to `@TP:` payload arguments: strength=F3, water=F4, milk=F6,
 * temperature=F7, bypass=F10.
 *
 * Reference data, not user state — a saved preset references a product by [code]
 * and stores the chosen values.
 */
data class Product(
    val code: Int,
    val name: String,
    val kind: ProductKind,
    val strength: Range? = null,
    val water: Range? = null,
    val milk: Range? = null,
    val bypass: Range? = null,
    val defaultTemperature: Temperature? = null,
) {
    val hasStrength: Boolean get() = strength != null
    val hasWater: Boolean get() = water != null
    val hasMilk: Boolean get() = milk != null
    val hasBypass: Boolean get() = bypass != null
    val hasTemperature: Boolean get() = defaultTemperature != null
}
