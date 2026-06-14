package automatl.juras.protocol

/** Returns the JSON text of a bundled machine catalog, or null if not found. */
internal expect fun loadCatalogJson(modelId: String): String?
