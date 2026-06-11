package automatl.juras.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import automatl.juras.domain.PairedDevice

@Composable
fun SettingsScreen(
    device: PairedDevice?,
    onEditConnection: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
                    Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
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
    }
}
