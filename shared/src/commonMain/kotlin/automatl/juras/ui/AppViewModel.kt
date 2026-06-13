package automatl.juras.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.domain.AppState
import automatl.juras.domain.AppStateStore
import automatl.juras.domain.BrewPreset
import automatl.juras.domain.ConfigCodec
import automatl.juras.domain.DefaultPresets
import automatl.juras.domain.ExportedConfig
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.product.Ef1030Catalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the persisted [AppState] and the mutations on it. Shared across screens
 * (created once at the app root and passed down). [state] is `null` until the
 * first value loads from the store, so the UI can show a splash and pick the
 * correct start destination once.
 */
class AppViewModel(private val store: AppStateStore) : ViewModel() {

    val state: StateFlow<AppState?> = store.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pendingBrew = MutableStateFlow<BrewPreset?>(null)
    val pendingBrew: StateFlow<BrewPreset?> = _pendingBrew.asStateFlow()

    fun setPendingBrew(preset: BrewPreset) {
        _pendingBrew.value = preset
    }

    fun pairDevice(device: PairedDevice) {
        viewModelScope.launch {
            store.pairDevice(device, DefaultPresets.forProducts(Ef1030Catalog.products))
        }
    }

    fun savePairedDevice(device: PairedDevice) {
        viewModelScope.launch { store.setPairedDevice(device) }
    }

    fun unpair() {
        viewModelScope.launch { store.setPairedDevice(null) }
    }

    fun upsertPreset(preset: BrewPreset) {
        viewModelScope.launch { store.upsertPreset(preset) }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { store.deletePreset(id) }
    }

    fun reorderPresets(presets: List<BrewPreset>) {
        viewModelScope.launch { store.setPresets(presets) }
    }

    fun exportConfig(): String = ConfigCodec.encode(state.value ?: AppState())

    fun parseConfig(text: String): Result<ExportedConfig> = runCatching { ConfigCodec.decode(text) }

    fun applyConfig(config: ExportedConfig) {
        viewModelScope.launch { store.replaceConfig(config.pairedDevice, config.presets) }
    }
}
