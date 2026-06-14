package automatl.juras.protocol

internal actual fun loadCatalogJson(modelId: String): String? =
    MachineCatalog::class.java.getResourceAsStream("/catalogs/$modelId.json")
        ?.bufferedReader()?.readText()
