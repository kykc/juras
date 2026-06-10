package automatl.juras.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import automatl.juras.data.ConnectionConfig
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.MachineReport

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Juras", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Connect to a JURA machine on your local network and read its status.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ConnectionForm(config = config, onChange = viewModel::updateConfig)

        Button(
            onClick = viewModel::readMachine,
            enabled = state != ReadState.Loading && config.host.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state == ReadState.Loading) "Reading…" else "Read machine")
        }

        ResultSection(state)
    }
}

@Composable
private fun ConnectionForm(config: ConnectionConfig, onChange: (ConnectionConfig) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = config.host,
                onValueChange = { onChange(config.copy(host = it.trim())) },
                label = { Text("Machine IP address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = config.setupPin,
                onValueChange = { onChange(config.copy(setupPin = it)) },
                label = { Text("Setup PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = config.deviceName,
                onValueChange = { onChange(config.copy(deviceName = it)) },
                label = { Text("Device name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = config.token,
                onValueChange = { onChange(config.copy(token = it.trim())) },
                label = { Text("Auth token") },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Settings are saved automatically on this device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultSection(state: ReadState) {
    when (state) {
        ReadState.Idle -> Unit
        ReadState.Loading -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }

        is ReadState.Error -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Error", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error)
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is ReadState.Success -> SuccessCard(state.auth, state.report)
    }
}

@Composable
private fun SuccessCard(auth: AuthResult, report: MachineReport?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (auth) {
                AuthResult.Authenticated -> Text(
                    "Authenticated", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                is AuthResult.PinRequired -> {
                    Text("Pairing required", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The machine issued a pairing token. Pairing isn't implemented " +
                            "yet — capture/enter a valid token to read data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("token: ${auth.token}", style = MaterialTheme.typography.labelSmall)
                }
                is AuthResult.Rejected -> {
                    Text("Rejected", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error)
                    Text(
                        "Machine response: ${auth.response}. Check the token and setup PIN.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (report != null) {
                Section("Product counts")
                if (report.productCounts.isEmpty()) EmptyLine()
                report.productCounts.forEach { KeyValue(it.name, it.count.toString()) }

                Section("Maintenance status (% to service)")
                if (report.maintenanceStatus.isEmpty()) EmptyLine()
                report.maintenanceStatus.forEach {
                    KeyValue(it.name, if (it.notApplicable) "n/a" else "${it.percent}%")
                }

                Section("Maintenance counters (cycles)")
                if (report.maintenanceCounters.isEmpty()) EmptyLine()
                report.maintenanceCounters.forEach { KeyValue(it.name, it.cycles.toString()) }

                Section("Machine state")
                val st = report.machineState
                if (st == null) {
                    EmptyLine()
                } else {
                    if (st.activeAlerts.isEmpty()) KeyValue("Active alerts", "none")
                    st.activeAlerts.forEach { KeyValue("bit ${it.bit}", it.name) }
                    KeyValue("raw", st.rawHex.ifEmpty { "—" })
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyLine() {
    Text("(none reported)", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
