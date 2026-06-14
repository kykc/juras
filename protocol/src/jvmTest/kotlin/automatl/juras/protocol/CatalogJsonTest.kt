package automatl.juras.protocol

import automatl.juras.protocol.product.Ef1030Catalog
import automatl.juras.protocol.product.ProductKind
import automatl.juras.protocol.Temperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogJsonTest {

    private val minimalJson = """
        {
          "modelId": "TEST01",
          "products": [
            {
              "code": 2,
              "name": "Espresso",
              "kind": "COFFEE",
              "strength": { "default": 8, "min": 1, "max": 10, "step": 1 },
              "water":    { "default": 45, "min": 15, "max": 80, "step": 5 },
              "defaultTemperature": "HIGH"
            },
            {
              "code": 8,
              "name": "Milk Foam",
              "kind": "MILK",
              "milk": { "default": 22, "min": 1, "max": 45, "step": 1 }
            }
          ],
          "alertBits": {
            "1": "Fill water",
            "13": "Coffee ready"
          },
          "brewStateNames": {
            "0x21": "Heating up",
            "34": "Press button"
          },
          "dispensingStates": ["0x34", "0x37", "0x3C", "0x41"],
          "maintenanceStatusFields": ["Cleaning", "Filter change", "Descaling"],
          "maintenanceCounterFields": ["Cleaning", "Filter change", "Descaling", "Cappu rinse", "Coffee rinse", "Cappu clean"]
        }
    """.trimIndent()

    @Test
    fun parsesModelId() {
        assertEquals("TEST01", MachineCatalog.fromJson(minimalJson).modelId)
    }

    @Test
    fun parsesProducts() {
        val catalog = MachineCatalog.fromJson(minimalJson)
        assertEquals(2, catalog.products.size)

        val espresso = catalog.productByCode(0x02)!!
        assertEquals("Espresso", espresso.name)
        assertEquals(ProductKind.COFFEE, espresso.kind)
        assertEquals(8, espresso.strength?.default)
        assertEquals(45, espresso.water?.default)
        assertEquals(Temperature.HIGH, espresso.defaultTemperature)
        assertNull(espresso.milk)

        val milk = catalog.productByCode(0x08)!!
        assertEquals(ProductKind.MILK, milk.kind)
        assertNull(milk.water)
        assertNull(milk.strength)
        assertEquals(22, milk.milk?.default)
    }

    @Test
    fun parsesAlertBitsWithDecimalKeys() {
        val catalog = MachineCatalog.fromJson(minimalJson)
        assertEquals("Fill water", catalog.alertBits[1])
        assertEquals("Coffee ready", catalog.alertBits[13])
    }

    @Test
    fun parsesBrewStateNamesWithHexAndDecimalKeys() {
        val catalog = MachineCatalog.fromJson(minimalJson)
        // hex key "0x21" (= 33) and decimal key "34" (= 0x22) — both forms work
        assertEquals("Heating up", catalog.brewStateNames[0x21])
        assertEquals("Press button", catalog.brewStateNames[34])
        // same value accessed via equivalent expressions
        assertEquals(catalog.brewStateNames[33], catalog.brewStateNames[0x21])
    }

    @Test
    fun parsesDispensingStates() {
        val catalog = MachineCatalog.fromJson(minimalJson)
        assertEquals(setOf(0x34, 0x37, 0x3C, 0x41), catalog.dispensingStates)
    }

    @Test
    fun parsesMaintenanceFields() {
        val catalog = MachineCatalog.fromJson(minimalJson)
        assertEquals(listOf("Cleaning", "Filter change", "Descaling"), catalog.maintenanceStatusFields)
        assertEquals(6, catalog.maintenanceCounterFields.size)
    }

    @Test
    fun ignoresUnknownFields() {
        val withExtra = minimalJson.replace(
            "\"modelId\": \"TEST01\"",
            "\"modelId\": \"TEST01\", \"futureField\": 42",
        )
        assertEquals("TEST01", MachineCatalog.fromJson(withExtra).modelId)
    }

    /** Spot-checks that Ef1030Catalog and the JSON agree on key data points. */
    @Test
    fun ef1030HardcodedMatchesKnownValues() {
        assertEquals("Coffee ready", Ef1030Catalog.alertBits[13])
        assertEquals("Fill water", Ef1030Catalog.alertBits[1])
        assertEquals("Heating up", Ef1030Catalog.brewStateNames[0x21])
        assertEquals("Enjoy", Ef1030Catalog.brewStateNames[0x3E])
        assertEquals(setOf(0x34, 0x37, 0x3C, 0x41), Ef1030Catalog.dispensingStates)
    }
}
