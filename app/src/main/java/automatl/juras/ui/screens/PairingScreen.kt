package automatl.juras.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import automatl.juras.domain.ExportedConfig
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.discovery.DiscoveredMachine
import automatl.juras.ui.DiscoveryState
import automatl.juras.ui.PairingState
import automatl.juras.ui.PairingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PairingScreen(
    existing: PairedDevice?,
    onSaved: (PairedDevice) -> Unit,
    parseConfig: (String) -> Result<ExportedConfig>,
    applyConfig: (ExportedConfig) -> Unit,
    onImported: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = viewModel(),
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ExportedConfig?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    }.getOrNull()
                }
                if (text == null) {
                    importError = "Couldn't read the selected file."
                } else {
                    parseConfig(text).fold(
                        onSuccess = { pendingImport = it },
                        onFailure = { importError = it.message ?: "Invalid config file." },
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connect to machine", style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = {
                    advanced = !advanced
                    viewModel.resetPairing()
                },
            ) {
                Text(if (advanced) "Use guided pairing" else "Enter manually (advanced)")
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text("Import config")
            }
        }

        if (advanced) {
            AdvancedPairing(existing = existing, onSaved = onSaved)
        } else {
            GuidedPairing(viewModel = viewModel, existing = existing, onSaved = onSaved)
        }
    }

    pendingImport?.let { config ->
        val devicePart = config.pairedDevice?.let { "machine \"${it.displayName}\" and " } ?: ""
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace configuration?") },
            text = {
                Text(
                    "This will replace your current $devicePart${config.presets.size} preset(s) " +
                        "with the imported ones. Your present configuration will be overwritten.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    applyConfig(config)
                    pendingImport = null
                    onImported()
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun GuidedPairing(
    viewModel: PairingViewModel,
    existing: PairedDevice?,
    onSaved: (PairedDevice) -> Unit,
) {
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()

    var selectedHost by rememberSaveable { mutableStateOf(existing?.host ?: "") }
    var selectedMachineName by rememberSaveable { mutableStateOf(existing?.machineName ?: "") }
    var label by rememberSaveable { mutableStateOf(existing?.label ?: android.os.Build.MODEL) }
    var setupPin by rememberSaveable { mutableStateOf(existing?.setupPin ?: "") }

    LaunchedEffect(pairing) {
        (pairing as? PairingState.Success)?.let { onSaved(it.device) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Discovery
        Text("1. Find your machine", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { viewModel.scan() },
            enabled = discovery != DiscoveryState.Scanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (discovery == DiscoveryState.Scanning) "Scanning…" else "Scan network")
        }
        when (val d = discovery) {
            DiscoveryState.Idle -> Unit
            DiscoveryState.Scanning -> Spinner()
            is DiscoveryState.Results -> {
                if (d.machines.isEmpty()) {
                    Text(
                        "No machines found. Make sure you're on the same Wi-Fi, or use " +
                            "manual entry.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    d.machines.forEach { machine ->
                        MachineCard(
                            machine = machine,
                            selected = machine.host == selectedHost,
                            onClick = {
                                selectedHost = machine.host
                                selectedMachineName = machine.name
                            },
                        )
                    }
                }
            }
            is DiscoveryState.Failed -> Text(
                d.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (selectedHost.isNotBlank()) {
            Text("Selected: $selectedHost", style = MaterialTheme.typography.bodySmall)
        }

        // 2. Details
        Text("2. Device details", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("This device's name") },
            supportingText = { Text("Shown on the machine when it asks to confirm pairing") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = setupPin,
            onValueChange = { setupPin = it },
            label = { Text("Setup PIN") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // 3. Pairing handshake
        Text("3. Pair", style = MaterialTheme.typography.titleMedium)
        when (val p = pairing) {
            PairingState.Idle, is PairingState.Failed -> {
                if (p is PairingState.Failed) {
                    Text(
                        p.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Tap Start pairing — the machine will then ask you to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        viewModel.startPairing(selectedHost, selectedMachineName, label, setupPin)
                    },
                    enabled = selectedHost.isNotBlank() && setupPin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start pairing")
                }
            }
            PairingState.AwaitingConfirmation -> {
                Text(
                    "Confirm \"pair with this device?\" on the machine display.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LabeledSpinner("Waiting for confirmation…")
                OutlinedButton(
                    onClick = { viewModel.resetPairing() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel") }
            }
            is PairingState.Success -> Text(
                "Paired!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MachineCard(machine: DiscoveredMachine, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                machine.name.ifBlank { "JURA machine" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${machine.host}${if (machine.firmware.isNotBlank()) " · ${machine.firmware}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvancedPairing(existing: PairedDevice?, onSaved: (PairedDevice) -> Unit) {
    var host by rememberSaveable { mutableStateOf(existing?.host ?: "") }
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "Juras") }
    var pin by rememberSaveable { mutableStateOf(existing?.setupPin ?: "") }
    var token by rememberSaveable { mutableStateOf(existing?.token ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Enter the machine's IP and a known auth token directly.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it.trim() },
            label = { Text("Machine IP address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Setup PIN") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it.trim() },
            label = { Text("Auth token") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onSaved(
                    PairedDevice(
                        host = host.trim(),
                        label = label.ifBlank { "Juras" },
                        setupPin = pin.trim(),
                        token = token.trim(),
                    ),
                )
            },
            enabled = host.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save & connect")
        }
    }
}

@Composable
private fun Spinner() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun LabeledSpinner(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
