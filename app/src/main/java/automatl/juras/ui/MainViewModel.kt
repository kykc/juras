package automatl.juras.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import automatl.juras.data.ConnectionConfig
import automatl.juras.data.JuraSettings
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

/** UI state for the single read-flow screen. */
sealed interface ReadState {
    data object Idle : ReadState
    data object Loading : ReadState
    data class Success(val auth: AuthResult, val report: MachineReport?) : ReadState
    data class Error(val message: String) : ReadState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = JuraSettings(application)

    private val _config = MutableStateFlow(settings.load())
    val config: StateFlow<ConnectionConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state.asStateFlow()

    fun updateConfig(config: ConnectionConfig) {
        _config.value = config
        settings.save(config)
    }

    /** Connect, authenticate, and pull a read-only report off the main thread. */
    fun readMachine() {
        val config = _config.value
        if (config.host.isBlank()) {
            _state.value = ReadState.Error("Enter the machine IP address first.")
            return
        }
        if (_state.value == ReadState.Loading) return

        _state.value = ReadState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { performRead(config) }
            }
            _state.value = result.fold(
                onSuccess = { it },
                onFailure = { ReadState.Error(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    private fun performRead(config: ConnectionConfig): ReadState {
        JuraConnection(host = config.host).use { conn ->
            conn.connect()
            val client = JuraClient(conn)
            return when (val auth = client.authenticate(config.credentialsOrNull())) {
                is AuthResult.Authenticated -> ReadState.Success(auth, client.readReport())
                else -> ReadState.Success(auth, null)
            }
        }
    }
}
