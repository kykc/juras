package automatl.juras.domain

import kotlinx.coroutines.flow.Flow

/** Platform-agnostic persistence layer for [AppState]. */
interface AppStateStore {
    val state: Flow<AppState>
    suspend fun setPairedDevice(device: PairedDevice?)
    suspend fun pairDevice(device: PairedDevice, seedIfEmpty: List<BrewPreset>)
    suspend fun upsertPreset(preset: BrewPreset)
    suspend fun deletePreset(id: String)
    suspend fun setPresets(presets: List<BrewPreset>)
    suspend fun replaceConfig(device: PairedDevice?, presets: List<BrewPreset>)
    suspend fun setDarkModePreference(pref: DarkModePreference)
}
