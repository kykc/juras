package automatl.juras.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.JuraClient
import automatl.juras.protocol.client.MachineReport
import automatl.juras.protocol.client.MachineState
import automatl.juras.protocol.MachineCatalog
import automatl.juras.protocol.client.MachineStateDecoder
import automatl.juras.protocol.transport.JuraConnection
import automatl.juras.protocol.transport.JuraUdpStatusClient
import automatl.juras.protocol.transport.UdpStatusReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the Status screen's read flow. */
sealed interface ReadState {
    data object Idle : ReadState
    data object Loading : ReadState
    data class Success(val auth: AuthResult, val report: MachineReport?) : ReadState
    data class Error(val message: String) : ReadState
}

/** UI state for the UDP status poll (no TCP, no auth). */
sealed interface UdpState {
    data object Idle : UdpState
    data object Loading : UdpState
    data class Success(
        val reply: UdpStatusReply,
        val decoded: MachineState?,
        val receivedAtMillis: Long,
    ) : UdpState
    data class Error(val message: String) : UdpState
}

/** Connects to the paired machine and pulls a read-only status report. */
class StatusViewModel : ViewModel() {

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state.asStateFlow()

    private val _udp = MutableStateFlow<UdpState>(UdpState.Idle)
    val udp: StateFlow<UdpState> = _udp.asStateFlow()

    private var liveJob: Job? = null

    /**
     * Continuously poll live status over UDP (J.O.E.'s channel) — connectionless and
     * auth-free, so it never contends for the machine's single TCP session. Driven by
     * the Status screen's lifecycle ([startLive]/[stopLive]). The TCP path is separate.
     */
    fun startLive(device: PairedDevice) {
        liveJob?.cancel()
        if (_udp.value !is UdpState.Success) _udp.value = UdpState.Loading
        liveJob = viewModelScope.launch {
            while (isActive) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val reply = JuraUdpStatusClient(device.host).poll()
                        val catalog = MachineCatalog.forModel(device.model)
                        val decoded = if (!reply.productRunning && reply.payloadHex.isNotEmpty()) {
                            runCatching { MachineStateDecoder.decode(reply.payloadHex, catalog) }.getOrNull()
                        } else {
                            null
                        }
                        UdpState.Success(reply, decoded, System.currentTimeMillis())
                    }
                }
                _udp.value = result.fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        (_udp.value as? UdpState.Success)
                            ?: UdpState.Error(e.message ?: e::class.simpleName ?: "Error")
                    },
                )
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    fun refresh(device: PairedDevice) {
        if (_state.value == ReadState.Loading) return
        _state.value = ReadState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { performRead(device) }.recoverCatching {
                    delay(RETRY_DELAY_MS)
                    performRead(device)
                }
            }
            _state.value = result.fold(
                onSuccess = { it },
                onFailure = { ReadState.Error(it.message ?: it::class.simpleName ?: "Error") },
            )
        }
    }

    private fun performRead(device: PairedDevice): ReadState {
        val catalog = MachineCatalog.forModel(device.model)
        JuraConnection(host = device.host, readTimeoutMs = 6_000).use { conn ->
            conn.connect()
            val client = JuraClient(conn, catalog)
            return when (val auth = client.authenticate(device.credentialsOrNull())) {
                is AuthResult.Authenticated -> ReadState.Success(auth, client.readReport())
                else -> ReadState.Success(auth, null)
            }
        }
    }

    override fun onCleared() {
        stopLive()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1_500L
        private const val RETRY_DELAY_MS = 1_500L
    }
}
