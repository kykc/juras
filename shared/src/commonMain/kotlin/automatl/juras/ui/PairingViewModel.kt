package automatl.juras.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.JuraCredentials
import automatl.juras.protocol.MachineCatalog
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.JuraClient
import automatl.juras.protocol.discovery.DiscoveredMachine
import automatl.juras.protocol.discovery.JuraDiscovery
import automatl.juras.protocol.transport.JuraConnection
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the network scan for machines. */
sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data object Scanning : DiscoveryState
    data class Results(val machines: List<DiscoveredMachine>) : DiscoveryState
    data class Failed(val message: String) : DiscoveryState
}

/**
 * State of the guided pairing handshake. Real flow on this machine (EF1030):
 *
 * 1. `@HP:<setupPIN>,<nameHex>,` (empty token) is sent; the machine shows a
 *    "pair with this device?" prompt and **withholds its reply until the user
 *    confirms on the machine**.
 * 2. On confirmation the machine replies `@hp4:<TOKEN>` — receiving the token *is*
 *    the success signal. The token is stored as the device's credential.
 */
sealed interface PairingState {
    data object Idle : PairingState
    /** Request sent; waiting for the user to confirm on the machine. */
    data object AwaitingConfirmation : PairingState
    data class Success(val device: PairedDevice) : PairingState
    data class Failed(val message: String) : PairingState
}

class PairingViewModel : ViewModel() {

    private val _discovery = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discovery: StateFlow<DiscoveryState> = _discovery.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    private var connection: JuraConnection? = null
    private var pairingJob: Job? = null

    fun scan() {
        if (_discovery.value == DiscoveryState.Scanning) return
        _discovery.value = DiscoveryState.Scanning
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { JuraDiscovery.discover() } }
            _discovery.value = result.fold(
                onSuccess = { DiscoveryState.Results(it) },
                onFailure = { DiscoveryState.Failed(it.message ?: "Scan failed") },
            )
        }
    }

    /**
     * Send the pairing request and wait (with a generous read timeout) for the
     * user to confirm on the machine. Receiving `@hp4:<TOKEN>` means confirmed.
     */
    fun startPairing(host: String, machineName: String, deviceName: String, setupPin: String) {
        if (_pairing.value == PairingState.AwaitingConfirmation) return
        closeConnection()
        val name = deviceName.ifBlank { "Juras" }
        val nameHex = JuraCredentials.deviceNameToHex(name)
        _pairing.value = PairingState.AwaitingConfirmation

        pairingJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Long read timeout: the reply only arrives after the user
                    // confirms on the machine, which can take a while.
                    val conn = JuraConnection(host, readTimeoutMs = 90_000)
                    connection = conn
                    conn.connect()
                    JuraClient(conn, MachineCatalog.forModel("EF1030")).authenticate(JuraCredentials(setupPin.trim(), nameHex, token = ""))
                }
            }
            withContext(Dispatchers.IO) { closeConnection() }

            // Ignore the result if pairing was cancelled/reset meanwhile.
            if (_pairing.value != PairingState.AwaitingConfirmation) return@launch

            _pairing.value = result.fold(
                onSuccess = { auth ->
                    when (auth) {
                        is AuthResult.PinRequired -> PairingState.Success(
                            PairedDevice(
                                host = host,
                                label = name,
                                setupPin = setupPin.trim(),
                                token = auth.token,
                                machineName = machineName,
                            ),
                        )
                        AuthResult.Authenticated -> PairingState.Failed(
                            "Machine accepted but issued no token. Use the Advanced tab " +
                                "with a known token.",
                        )
                        is AuthResult.Rejected -> PairingState.Failed(
                            "Machine rejected the request (response: ${auth.response}). " +
                                "Check the setup PIN.",
                        )
                    }
                },
                onFailure = { e ->
                    if (e is UnknownHostException) {
                        PairingState.Failed(
                            "Cannot resolve \"$host\" — check the address and your network.",
                        )
                    } else {
                        PairingState.Failed(
                            "No confirmation received. Confirm \"pair with this device?\" on the " +
                                "machine within a minute or so, then try again.",
                        )
                    }
                },
            )
        }
    }

    fun resetPairing() {
        pairingJob?.cancel()
        pairingJob = null
        closeConnection()
        _pairing.value = PairingState.Idle
    }

    private fun closeConnection() {
        connection?.let { runCatching { it.close() } }
        connection = null
    }

    override fun onCleared() {
        closeConnection()
    }
}
