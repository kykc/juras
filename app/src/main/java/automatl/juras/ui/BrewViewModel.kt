package automatl.juras.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.domain.BrewPreset
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.BrewProgress
import automatl.juras.protocol.client.BrewStateNames
import automatl.juras.protocol.client.JuraClient
import automatl.juras.protocol.client.TpPayload
import automatl.juras.protocol.product.Ef1030Catalog
import automatl.juras.protocol.transport.JuraConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BrewUiState {
    /** Before the user confirms — show preset details and a Start button. */
    data object Idle : BrewUiState
    data object Connecting : BrewUiState
    data class Brewing(
        val phase: String,
        val percent: Int? = null,
        val doneMl: Int? = null,
        val totalMl: Int? = null,
    ) : BrewUiState
    data class Done(val message: String, val success: Boolean = true) : BrewUiState
    data class Failed(val message: String) : BrewUiState
}

class BrewViewModel : ViewModel() {

    private val _state = MutableStateFlow<BrewUiState>(BrewUiState.Idle)
    val state: StateFlow<BrewUiState> = _state.asStateFlow()

    private var connection: JuraConnection? = null
    private var inProgress = false

    fun start(device: PairedDevice, preset: BrewPreset) {
        if (inProgress) return
        inProgress = true
        _state.value = BrewUiState.Connecting
        val payload = buildPayload(preset)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    JuraConnection(device.host, readTimeoutMs = 60_000).use { conn ->
                        connection = conn
                        conn.connect()
                        val client = JuraClient(conn)
                        val auth = client.authenticate(device.credentialsOrNull())
                        check(auth is AuthResult.Authenticated) {
                            "Authentication failed — re-pair the machine."
                        }
                        client.brew(payload) { progress ->
                            _state.value = progress.toUiState()
                        }
                    }
                }
            }
            connection = null
            inProgress = false
            _state.value = result.fold(
                onSuccess = { outcome ->
                    if (outcome.completed) {
                        BrewUiState.Done("Done — enjoy!", success = true)
                    } else {
                        // A non-zero finish status is a state code (same space as @tv),
                        // e.g. 0x40 = "Fill water tank".
                        val reason = outcome.statusByte?.let { BrewStateNames.nameFor(it) }
                        val message = reason?.let { "Couldn't complete — $it" }
                            ?: "Finished (status 0x%02X)".format(outcome.statusByte ?: 0)
                        BrewUiState.Done(message, success = false)
                    }
                },
                onFailure = { BrewUiState.Failed(it.message ?: "Brew failed") },
            )
        }
    }

    /** Ask the machine to stop the current product (`@TG:FF`). */
    fun stop() {
        val conn = connection ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { conn.send("@TG:FF") } }
    }

    private fun buildPayload(preset: BrewPreset): String {
        val product = Ef1030Catalog.byCode(preset.productCode)
        return TpPayload.build(
            productCode = preset.productCode,
            strength = if (product == null || product.hasStrength) preset.strength else null,
            waterMl = if (product == null || product.hasWater) preset.waterMl else null,
            milkMl = preset.milkMl,
            temperature = if (product == null || product.hasTemperature) preset.temperature else null,
            bypassMl = preset.bypassMl,
        )
    }

    private fun BrewProgress.toUiState(): BrewUiState.Brewing = when (this) {
        BrewProgress.Started -> BrewUiState.Brewing("Brewing started")
        is BrewProgress.Phase -> BrewUiState.Brewing(label)
        is BrewProgress.Dispensing -> BrewUiState.Brewing(
            phase = "Dispensing",
            percent = percent,
            doneMl = doneMl,
            totalMl = totalMl,
        )
    }
}
