package automatl.juras.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.domain.BrewPreset
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.BrewProgress
import automatl.juras.protocol.client.BrewProgressDecoder
import automatl.juras.protocol.client.JuraClient
import automatl.juras.protocol.MachineCatalog
import automatl.juras.protocol.client.MachineStateDecoder
import automatl.juras.protocol.client.TpPayload
import automatl.juras.protocol.transport.JuraConnection
import automatl.juras.protocol.transport.JuraUdpStatusClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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

/**
 * Orchestrates a brew the way J.O.E. does: **commands over TCP, progress over UDP**.
 *
 * A brief TCP burst authenticates and fires `@TP` (then closes — the machine brews
 * autonomously). Progress is then observed by polling the connectionless UDP status
 * channel, so we never hold the machine's single TCP session open. Stop sends
 * `@TG:FF` in its own short TCP burst.
 */
class BrewViewModel : ViewModel() {

    private val _state = MutableStateFlow<BrewUiState>(BrewUiState.Idle)
    val state: StateFlow<BrewUiState> = _state.asStateFlow()

    private var inProgress = false
    private var brewJob: Job? = null
    private var currentDevice: PairedDevice? = null
    @Volatile private var abandoned = false

    fun start(device: PairedDevice, preset: BrewPreset) {
        if (inProgress) return
        inProgress = true
        abandoned = false
        currentDevice = device
        _state.value = BrewUiState.Connecting
        val payload = buildPayload(preset, device)

        brewJob = viewModelScope.launch {
            val result = runCatching {
                startProductWithRetry(device, payload)
                awaitCompletion(device)
            }
            if (abandoned) return@launch
            inProgress = false
            _state.value = result.fold(
                onSuccess = { it },
                onFailure = { BrewUiState.Failed(it.message ?: "Brew failed") },
            )
        }
    }

    private suspend fun startProductWithRetry(device: PairedDevice, payload: String) {
        retryTcpOperation(
            shouldRetry = { it !is AuthenticationFailedException && isRetriableTcpFailure(it) },
        ) {
            withContext(Dispatchers.IO) { startProduct(device, payload) }
        }
    }

    private fun startProduct(device: PairedDevice, payload: String) {
        val catalog = MachineCatalog.forModel(device.model)
        JuraConnection(device.host).use { conn ->
            conn.connect()
            val client = JuraClient(conn, catalog)
            val auth = client.authenticate(device.credentialsOrNull())
            if (auth !is AuthResult.Authenticated) {
                throw AuthenticationFailedException("Authentication failed — re-pair the machine.")
            }
            client.startProduct(payload)
        }
    }

    /**
     * Poll the UDP status channel until the product finishes. While a product runs the
     * reply is a `@TV` progress frame; once it stops the reply is an idle `@TF`. If we
     * never see it start, the machine likely refused (needs water, etc.) — report the
     * blocking flag from `@TF`.
     */
    private suspend fun awaitCompletion(device: PairedDevice): BrewUiState {
        val udp = JuraUdpStatusClient(device.host)
        var seenRunning = false
        val startedAt = System.currentTimeMillis()
        _state.value = BrewUiState.Brewing("Starting…")

        while (coroutineContext.isActive && !abandoned) {
            val reply = withContext(Dispatchers.IO) { runCatching { udp.poll() }.getOrNull() }
            val elapsed = System.currentTimeMillis() - startedAt

            if (reply != null && reply.markerOk) {
                if (reply.productRunning) {
                    seenRunning = true
                    val catalog = MachineCatalog.forModel(device.model)
                    _state.value = BrewProgressDecoder.decode(reply.payloadHex, catalog).toUiState()
                } else if (seenRunning) {
                    return BrewUiState.Done("Done — enjoy!", success = true)
                } else if (elapsed > STARTUP_TIMEOUT_MS) {
                    return refusedState(reply.payloadHex, device)
                }
            }
            if (elapsed > MAX_BREW_MS) return BrewUiState.Failed("Brew timed out")
            delay(POLL_INTERVAL_MS)
        }
        return BrewUiState.Idle
    }

    private fun refusedState(tfPayloadHex: String, device: PairedDevice): BrewUiState {
        val catalog = MachineCatalog.forModel(device.model)
        val reason = MachineStateDecoder.decode(tfPayloadHex, catalog).activeAlerts.firstOrNull()?.name
        return BrewUiState.Done(
            reason?.let { "Couldn't start — $it" } ?: "Couldn't start the product",
            success = false,
        )
    }

    fun stop() {
        val device = currentDevice ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val catalog = MachineCatalog.forModel(device.model)
                JuraConnection(device.host).use { conn ->
                    conn.connect()
                    val client = JuraClient(conn, catalog)
                    client.authenticate(device.credentialsOrNull())
                    client.cancelProduct()
                }
            }
        }
    }

    /**
     * Forcibly abandon brewing **without talking to the machine** — cancels the UDP
     * watch loop locally; the machine keeps doing whatever it's doing.
     */
    fun forceQuit() {
        abandoned = true
        inProgress = false
        brewJob?.cancel()
        brewJob = null
        _state.value = BrewUiState.Idle
    }

    override fun onCleared() {
        brewJob?.cancel()
    }

    private fun buildPayload(preset: BrewPreset, device: PairedDevice): String {
        val catalog = MachineCatalog.forModel(device.model)
        val product = catalog.productByCode(preset.productCode)
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

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
        private const val STARTUP_TIMEOUT_MS = 15_000L
        private const val MAX_BREW_MS = 5 * 60_000L
    }

    private class AuthenticationFailedException(message: String) : Exception(message)
}
