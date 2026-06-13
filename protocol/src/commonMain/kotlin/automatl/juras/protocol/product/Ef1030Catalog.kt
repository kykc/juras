package automatl.juras.protocol.product

import automatl.juras.protocol.Temperature

/**
 * Product catalogue for the Jura E6 / EF1030, derived from protocol reverse
 * engineering and hardware verification. All active products are included;
 * Powder Product is omitted (inactive on this model).
 * Water/milk/bypass are in ml; strength is 1–10.
 *
 * 2 Espressi / 2 Coffee have no strength; Milk Foam has only milk amount;
 * Café Barista / Barista Lungo add bypass. Other models define their own catalogue.
 */
object Ef1030Catalog {

    val products: List<Product> = listOf(
        Product(
            code = 0x02, name = "Espresso", kind = ProductKind.COFFEE,
            strength = Range(default = 8, min = 1, max = 10, step = 1),
            water = Range(default = 45, min = 15, max = 80, step = 5),
            defaultTemperature = Temperature.HIGH,
        ),
        Product(
            code = 0x03, name = "Coffee", kind = ProductKind.COFFEE,
            strength = Range(default = 5, min = 1, max = 10, step = 1),
            water = Range(default = 100, min = 25, max = 240, step = 5),
            defaultTemperature = Temperature.NORMAL,
        ),
        Product(
            code = 0x04, name = "Cappuccino", kind = ProductKind.COFFEE_MILK,
            strength = Range(default = 8, min = 1, max = 10, step = 1),
            water = Range(default = 60, min = 25, max = 240, step = 5),
            milk = Range(default = 12, min = 1, max = 45, step = 1),
            defaultTemperature = Temperature.NORMAL,
        ),
        Product(
            code = 0x06, name = "Espresso Macchiato", kind = ProductKind.COFFEE_MILK,
            strength = Range(default = 8, min = 1, max = 10, step = 1),
            water = Range(default = 25, min = 15, max = 80, step = 5),
            milk = Range(default = 3, min = 1, max = 45, step = 1),
            defaultTemperature = Temperature.HIGH,
        ),
        Product(
            code = 0x28, name = "Caffé Barista", kind = ProductKind.COFFEE,
            strength = Range(default = 6, min = 1, max = 10, step = 1),
            water = Range(default = 60, min = 25, max = 240, step = 5),
            bypass = Range(default = 40, min = 0, max = 240, step = 5),
            defaultTemperature = Temperature.NORMAL,
        ),
        Product(
            code = 0x29, name = "Barista Lungo", kind = ProductKind.COFFEE,
            strength = Range(default = 7, min = 1, max = 10, step = 1),
            water = Range(default = 120, min = 25, max = 240, step = 5),
            bypass = Range(default = 100, min = 0, max = 240, step = 5),
            defaultTemperature = Temperature.NORMAL,
        ),
        Product(
            code = 0x08, name = "Milk Foam", kind = ProductKind.MILK,
            milk = Range(default = 22, min = 1, max = 45, step = 1),
        ),
        Product(
            code = 0x0D, name = "Hot Water", kind = ProductKind.WATER,
            water = Range(default = 220, min = 25, max = 300, step = 5),
            defaultTemperature = Temperature.NORMAL,
        ),
        Product(
            code = 0x31, name = "2 Espressi", kind = ProductKind.COFFEE,
            water = Range(default = 45, min = 15, max = 80, step = 5),
            defaultTemperature = Temperature.HIGH,
        ),
        Product(
            code = 0x36, name = "2 Coffee", kind = ProductKind.COFFEE,
            water = Range(default = 100, min = 25, max = 240, step = 5),
            defaultTemperature = Temperature.NORMAL,
        ),
    )

    fun byCode(code: Int): Product? = products.firstOrNull { it.code == code }
}
