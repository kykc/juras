package automatl.juras.protocol.product

import automatl.juras.protocol.Temperature

/**
 * Product catalogue for the Jura E6 / EF1030. Values are the validated set from
 * `../jura.py` (`PRODUCTS`) and the EF1030 machine XML defaults/ranges. Water
 * amounts are in ml and must be multiples of [Product.waterStepMl].
 *
 * Other models define their own catalogue; later this will be parsed from the
 * machine XML rather than hardcoded.
 */
object Ef1030Catalog {

    val products: List<Product> = listOf(
        Product(
            code = 0x02, name = "Espresso", kind = ProductKind.COFFEE,
            defaultStrength = 8, strengthMin = 1, strengthMax = 10,
            defaultWaterMl = 45, waterMinMl = 15, waterMaxMl = 100, waterStepMl = 5,
            defaultTemperature = Temperature.HIGH,
        ),
        Product(
            code = 0x03, name = "Coffee", kind = ProductKind.COFFEE,
            defaultStrength = 5, strengthMin = 1, strengthMax = 10,
            defaultWaterMl = 100, waterMinMl = 25, waterMaxMl = 240, waterStepMl = 5,
            defaultTemperature = Temperature.NORMAL,
        ),
        Product(
            code = 0x04, name = "Cappuccino", kind = ProductKind.COFFEE_MILK,
            defaultStrength = 8, strengthMin = 1, strengthMax = 10,
            defaultWaterMl = 60, waterMinMl = 25, waterMaxMl = 240, waterStepMl = 5,
            defaultTemperature = Temperature.NORMAL, defaultMilkMl = 12,
        ),
        Product(
            code = 0x06, name = "Espresso Macchiato", kind = ProductKind.COFFEE_MILK,
            defaultStrength = 8, strengthMin = 1, strengthMax = 10,
            defaultWaterMl = 45, waterMinMl = 15, waterMaxMl = 100, waterStepMl = 5,
            defaultTemperature = Temperature.HIGH, defaultMilkMl = 6,
        ),
        Product(
            code = 0x0D, name = "Hot Water", kind = ProductKind.WATER,
            defaultStrength = 0, strengthMin = 0, strengthMax = 0,
            defaultWaterMl = 100, waterMinMl = 25, waterMaxMl = 400, waterStepMl = 5,
            defaultTemperature = Temperature.NORMAL,
        ),
        Product(
            code = 0x31, name = "2 Espressi", kind = ProductKind.COFFEE,
            defaultStrength = 8, strengthMin = 1, strengthMax = 10,
            defaultWaterMl = 45, waterMinMl = 15, waterMaxMl = 100, waterStepMl = 5,
            defaultTemperature = Temperature.HIGH,
        ),
        Product(
            code = 0x36, name = "2 Coffee", kind = ProductKind.COFFEE,
            defaultStrength = 5, strengthMin = 1, strengthMax = 10,
            defaultWaterMl = 100, waterMinMl = 25, waterMaxMl = 240, waterStepMl = 5,
            defaultTemperature = Temperature.NORMAL,
        ),
    )

    fun byCode(code: Int): Product? = products.firstOrNull { it.code == code }
}
