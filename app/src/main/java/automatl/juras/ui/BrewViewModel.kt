package automatl.juras.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.domain.BrewPreset
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.BrewProgress
import automatl.juras.protocol.client.BrewProgressDecoder
import automatl.juras.protocol.client.JuraClient
import automatl.juras.protocol.client.MachineStateDecoder
import automatl.juras.protocol.client.TpPayload
import automatl.juras.protocol.product.Ef1030Catalog
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
 * channel, so we never hold the machine's single TCP session open or stream `@TS:01`
 * over it. Stop sends `@TG:FF` in its own short TCP burst.
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
        val payload = buildPayload(preset)

        brewJob = viewModelScope.launch {
            val result = runCatching {
                // 1. Fire the brew command over TCP, then close. The machine keeps
                //    brewing regardless of the connection.
                withContext(Dispatchers.IO) {
                    JuraConnection(device.host).use { conn ->
                        conn.connect()
                        val client = JuraClient(conn)
                        val auth = client.authenticate(device.credentialsOrNull())
                        check(auth is AuthResult.Authenticated) {
                            "Authentication failed — re-pair the machine."
                        }
                        client.startProduct(payload)
                    }
                }
                // 2. Watch the brew over UDP until it finishes.
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
                    _state.value = BrewProgressDecoder.decode(reply.payloadHex).toUiState()
                } else if (seenRunning) {
                    return BrewUiState.Done("Done — enjoy!", success = true)
                } else if (elapsed > STARTUP_TIMEOUT_MS) {
                    return refusedState(reply.payloadHex)
                }
            }
            if (elapsed > MAX_BREW_MS) return BrewUiState.Failed("Brew timed out")
            delay(POLL_INTERVAL_MS)
        }
        return BrewUiState.Idle
    }

    /** Describe why a product never started, using the active `@TF` flag if any. */
    private fun refusedState(tfPayloadHex: String): BrewUiState {
        val reason = MachineStateDecoder.decode(tfPayloadHex).activeAlerts.firstOrNull()?.name
        return BrewUiState.Done(
            reason?.let { "Couldn't start — $it" } ?: "Couldn't start the product",
            success = false,
        )
    }

    /** Ask the machine to stop the current product (`@TG:FF`) in a short TCP burst. */
    fun stop() {
        val device = currentDevice ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                JuraConnection(device.host).use { conn ->
                    conn.connect()
                    val client = JuraClient(conn)
                    client.authenticate(device.credentialsOrNull())
                    client.cancelProduct()
                }
            }
        }
        // The UDP loop will see the machine go idle and finish the brew.
    }

    /**
     * Forcibly abandon brewing **without talking to the machine** — cancels the UDP
     * watch loop locally; the machine keeps doing whatever it's doing. The screen
     * should navigate away after this.
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

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
        private const val STARTUP_TIMEOUT_MS = 15_000L
        private const val MAX_BREW_MS = 5 * 60_000L
    }
}
