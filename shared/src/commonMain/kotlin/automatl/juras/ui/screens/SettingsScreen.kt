package automatl.juras.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import automatl.juras.domain.ExportedConfig
import automatl.juras.domain.PairedDevice
import automatl.juras.ui.platform.rememberOpenFileLauncher
import automatl.juras.ui.platform.rememberSaveFileLauncher

@Composable
fun SettingsScreen(
    device: PairedDevice?,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onEditConnection: () -> Unit,
    onUnpair: () -> Unit,
    onRename: (String) -> Unit,
    exportConfig: () -> String,
    parseConfig: (String) -> Result<ExportedConfig>,
    applyConfig: (ExportedConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ExportedConfig?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberSaveFileLauncher(
        suggestedName = "juras-config.yaml",
        content = { exportConfig().toByteArray() },
        onDone = {},
    )

    val openLauncher = rememberOpenFileLauncher { text ->
        if (text != null) {
            parseConfig(text).fold(
                onSuccess = { pendingImport = it },
                onFailure = { importError = it.message ?: "Invalid config file." },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Paired machine", style = MaterialTheme.typography.titleMedium)
                if (device == null) {
                    Text("None", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { renaming = true }) { Text("Rename") }
                    }
                    Text(
                        "${device.host} · model ${device.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Paired as \"${device.label}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(onClick = onEditConnection, modifier = Modifier.fillMaxWidth()) {
            Text(if (device == null) "Connect a machine" else "Edit connection")
        }

        if (device != null) {
            OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
                Text("Unpair")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Configuration", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Export your machine and presets to a file, or import them onto another " +
                        "phone. The file contains your pairing token — keep it private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { saveLauncher() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export config") }
                OutlinedButton(
                    onClick = { openLauncher() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import config") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dark mode", style = MaterialTheme.typography.titleMedium)
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }
        }
    }

    if (renaming && device != null) {
        RenameDialog(
            current = device.displayName,
            onConfirm = {
                onRename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
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
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename machine") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
