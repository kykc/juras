package automatl.juras.ui

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (Navigation Compose 2.8+). */
sealed interface Route {
    @Serializable data object Brew : Route
    @Serializable data object Status : Route
    @Serializable data object Settings : Route
    @Serializable data object Pairing : Route

    @Serializable data class PresetEditor(val presetId: String? = null) : Route
    @Serializable data class Brewing(val presetId: String) : Route
}
