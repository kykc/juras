package automatl.juras.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.JuraClient
import automatl.juras.protocol.client.MachineReport
import automatl.juras.protocol.transport.JuraConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state for the Status screen's read flow. */
sealed interface ReadState {
    data object Idle : ReadState
    data object Loading : ReadState
    data class Success(val auth: AuthResult, val report: MachineReport?) : ReadState
    data class Error(val message: String) : ReadState
}

/** Connects to the paired machine and pulls a read-only status report. */
class StatusViewModel : ViewModel() {

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state.asStateFlow()

    fun refresh(device: PairedDevice) {
        if (_state.value == ReadState.Loading) return
        _state.value = ReadState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { performRead(device) } }
            _state.value = result.fold(
                onSuccess = { it },
                onFailure = { ReadState.Error(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    private fun performRead(device: PairedDevice): ReadState {
        JuraConnection(host = device.host).use { conn ->
            conn.connect()
            val client = JuraClient(conn)
            return when (val auth = client.authenticate(device.credentialsOrNull())) {
                is AuthResult.Authenticated -> ReadState.Success(auth, client.readReport())
                else -> ReadState.Success(auth, null)
            }
        }
    }
}
