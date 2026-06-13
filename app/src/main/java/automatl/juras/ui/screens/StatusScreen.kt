package automatl.juras.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.client.AuthResult
import automatl.juras.protocol.client.MachineReport
import automatl.juras.ui.ReadState
import automatl.juras.ui.StatusViewModel
import automatl.juras.ui.UdpState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(
    device: PairedDevice?,
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val udpState by viewModel.udp.collectAsStateWithLifecycle()

    // Live UDP polling runs only while this screen is shown for a paired device.
    if (device != null) {
        DisposableEffect(device) {
            viewModel.startLive(device)
            onDispose { viewModel.stopLive() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Status", style = MaterialTheme.typography.headlineMedium)

        if (device == null) {
            Text(
                "No machine paired. Connect one from the Settings tab.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        Text("${device.displayName} · ${device.host}", style = MaterialTheme.typography.bodyMedium)

        UdpSection(udpState)

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Text("Statistics", style = MaterialTheme.typography.titleSmall)
        Text(
            "Counters and maintenance are read over TCP on demand.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { viewModel.refresh(device) },
            enabled = state != ReadState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state == ReadState.Loading) "Reading…" else "Read statistics (TCP)")
        }

        ResultSection(state)
    }
}

@Composable
private fun UdpSection(state: UdpState) {
    when (state) {
        UdpState.Idle -> Unit
        UdpState.Loading -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }

        is UdpState.Error -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "No live status", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "The machine isn't answering the UDP poll (firewall/subnet, or not " +
                        "on WiFi). Retrying…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is UdpState.Success -> Card(modifier = Modifier.fillMaxWidth()) {
            val reply = state.reply
            var showRaw by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (reply.markerOk) "Live status" else "Live status (unrecognised reply)",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reply.markerOk) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Text(
                    "Received ${formatClock(state.receivedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Section(if (reply.productRunning) "Brewing (@TV)" else "Machine flags (@TF)")
                val decoded = state.decoded
                when {
                    reply.productRunning -> KeyValue("Payload", reply.payloadHex.ifEmpty { "—" })
                    decoded == null -> EmptyLine()
                    decoded.activeAlerts.isEmpty() -> KeyValue("Flags", "none")
                    else -> decoded.activeAlerts.forEach { KeyValue("bit ${it.bit}", it.name) }
                }

                Section("Machine")
                KeyValue("Module", reply.name.ifEmpty { "—" })
                KeyValue("Name", reply.machineId.ifEmpty { "—" })

                TextButton(
                    onClick = { showRaw = !showRaw },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(if (showRaw) "Hide raw packet" else "Show raw packet")
                }
                if (showRaw) {
                    Text(
                        reply.rawHex.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                Text(
                    "Error", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
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
                        "The machine issued a pairing token but pairing isn't implemented " +
                            "yet. Enter a valid token in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is AuthResult.Rejected -> {
                    Text(
                        "Rejected", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
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
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Text(
        title, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
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
    Text(
        "(none reported)", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatClock(millis: Long): String = clockFormat.format(Date(millis))
