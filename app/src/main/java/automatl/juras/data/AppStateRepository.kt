package automatl.juras.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import automatl.juras.domain.AppState
import automatl.juras.domain.AppStateStore
import automatl.juras.domain.BrewPreset
import automatl.juras.domain.DarkModePreference
import automatl.juras.domain.PairedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** JSON-backed DataStore serializer for [AppState]. */
private object AppStateSerializer : Serializer<AppState> {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Empty by default; presets are seeded from the machine catalogue on first pairing.
    override val defaultValue: AppState = AppState()

    override suspend fun readFrom(input: InputStream): AppState =
        try {
            json.decodeFromString(AppState.serializer(), input.readBytes().decodeToString())
        } catch (_: SerializationException) {
            defaultValue
        }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(json.encodeToString(AppState.serializer(), t).encodeToByteArray())
    }
}

private val Context.appStateDataStore: DataStore<AppState> by dataStore(
    fileName = "app_state.json",
    serializer = AppStateSerializer,
)

/**
 * Single source of truth for persisted app state. Exposes a reactive [state] and
 * suspend mutators. Replaces the old SharedPreferences-based settings.
 */
class AppStateRepository(private val context: Context) : AppStateStore {

    override val state: Flow<AppState> = context.appStateDataStore.data

    override suspend fun setPairedDevice(device: PairedDevice?) {
        context.appStateDataStore.updateData { it.copy(pairedDevice = device) }
    }

    /**
     * Pair a device and, if the preset list is empty (clean state), seed it with
     * [seedIfEmpty]. Existing presets are preserved (e.g. when re-pairing).
     */
    override suspend fun pairDevice(device: PairedDevice, seedIfEmpty: List<BrewPreset>) {
        context.appStateDataStore.updateData { current ->
            current.copy(
                pairedDevice = device,
                presets = if (current.presets.isEmpty()) seedIfEmpty else current.presets,
            )
        }
    }

    override suspend fun upsertPreset(preset: BrewPreset) {
        context.appStateDataStore.updateData { current ->
            val presets = if (current.presets.any { it.id == preset.id }) {
                // Edit: replace in place, keeping the preset's position.
                current.presets.map { if (it.id == preset.id) preset else it }
            } else {
                // New: add to the top of the list.
                listOf(preset) + current.presets
            }
            current.copy(presets = presets)
        }
    }

    override suspend fun deletePreset(id: String) {
        context.appStateDataStore.updateData { current ->
            current.copy(presets = current.presets.filterNot { it.id == id })
        }
    }

    /** Replace the preset list wholesale — used to persist a reordering. */
    override suspend fun setPresets(presets: List<BrewPreset>) {
        context.appStateDataStore.updateData { it.copy(presets = presets) }
    }

    /** Replace device + presets together — used when importing a config. */
    override suspend fun replaceConfig(device: PairedDevice?, presets: List<BrewPreset>) {
        context.appStateDataStore.updateData { it.copy(pairedDevice = device, presets = presets) }
    }

    override suspend fun setDarkModePreference(pref: DarkModePreference) {
        context.appStateDataStore.updateData { it.copy(darkModePreference = pref) }
    }

    override suspend fun setLeftHandedMode(enabled: Boolean) {
        context.appStateDataStore.updateData { it.copy(leftHandedMode = enabled) }
    }
}
