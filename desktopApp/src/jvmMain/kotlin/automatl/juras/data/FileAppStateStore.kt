package automatl.juras.data

import automatl.juras.domain.AppState
import automatl.juras.domain.AppStateStore
import automatl.juras.domain.BrewPreset
import automatl.juras.domain.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** JSON-file backed [AppStateStore] for desktop (JVM). */
class FileAppStateStore : AppStateStore {
    private val file = File(configDir(), "app_state.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())

    override val state: Flow<AppState> = _state.asStateFlow()

    private fun load(): AppState =
        runCatching { json.decodeFromString(AppState.serializer(), file.readText()) }
            .getOrDefault(AppState())

    private fun persist(s: AppState) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(AppState.serializer(), s))
    }

    private suspend fun update(transform: (AppState) -> AppState) = mutex.withLock {
        val new = transform(_state.value)
        withContext(Dispatchers.IO) { persist(new) }
        _state.value = new
    }

    override suspend fun setPairedDevice(device: PairedDevice?) =
        update { it.copy(pairedDevice = device) }

    override suspend fun pairDevice(device: PairedDevice, seedIfEmpty: List<BrewPreset>) =
        update { current ->
            current.copy(
                pairedDevice = device,
                presets = if (current.presets.isEmpty()) seedIfEmpty else current.presets,
            )
        }

    override suspend fun upsertPreset(preset: BrewPreset) =
        update { current ->
            val presets = if (current.presets.any { it.id == preset.id }) {
                current.presets.map { if (it.id == preset.id) preset else it }
            } else {
                listOf(preset) + current.presets
            }
            current.copy(presets = presets)
        }

    override suspend fun deletePreset(id: String) =
        update { it.copy(presets = it.presets.filterNot { p -> p.id == id }) }

    override suspend fun setPresets(presets: List<BrewPreset>) =
        update { it.copy(presets = presets) }

    override suspend fun replaceConfig(device: PairedDevice?, presets: List<BrewPreset>) =
        update { it.copy(pairedDevice = device, presets = presets) }
}

private fun configDir(): File {
    val os = System.getProperty("os.name")?.lowercase() ?: ""
    val home = System.getProperty("user.home") ?: "."
    return when {
        os.contains("mac") -> File(home, "Library/Application Support/Juras")
        os.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Juras")
        else -> File(home, ".config/juras")
    }
}
