package automatl.juras.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.data.AppStateRepository
import automatl.juras.domain.AppState
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
 * first value loads from DataStore, so the UI can show a splash and pick the
 * correct start destination once.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppStateRepository(application)

    val state: StateFlow<AppState?> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ephemeral preset for a one-time "quick brew" that is not saved. */
    private val _pendingBrew = MutableStateFlow<BrewPreset?>(null)
    val pendingBrew: StateFlow<BrewPreset?> = _pendingBrew.asStateFlow()

    fun setPendingBrew(preset: BrewPreset) {
        _pendingBrew.value = preset
    }

    /** Pair a device and seed default presets if none exist yet (clean-state OOBE). */
    fun pairDevice(device: PairedDevice) {
        viewModelScope.launch {
            repository.pairDevice(device, DefaultPresets.forProducts(Ef1030Catalog.products))
        }
    }

    fun savePairedDevice(device: PairedDevice) {
        viewModelScope.launch { repository.setPairedDevice(device) }
    }

    fun unpair() {
        viewModelScope.launch { repository.setPairedDevice(null) }
    }

    fun upsertPreset(preset: BrewPreset) {
        viewModelScope.launch { repository.upsertPreset(preset) }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { repository.deletePreset(id) }
    }

    fun reorderPresets(presets: List<BrewPreset>) {
        viewModelScope.launch { repository.setPresets(presets) }
    }

    // ── config import / export ───────────────────────────────────────────────

    /** Serialize the current device + presets to a YAML config document. */
    fun exportConfig(): String = ConfigCodec.encode(state.value ?: AppState())

    /** Parse + validate an imported config document (does not apply it). */
    fun parseConfig(text: String): Result<ExportedConfig> = runCatching { ConfigCodec.decode(text) }

    /** Replace the current device + presets with an imported config. */
    fun applyConfig(config: ExportedConfig) {
        viewModelScope.launch { repository.replaceConfig(config.pairedDevice, config.presets) }
    }
}
