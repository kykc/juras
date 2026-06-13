package automatl.juras.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import automatl.juras.domain.BrewPreset
import automatl.juras.domain.PairedDevice
import automatl.juras.protocol.product.Ef1030Catalog
import automatl.juras.ui.BrewUiState
import automatl.juras.ui.BrewViewModel

/** Seconds the successful "Done" screen waits before auto-returning to the brew list. */
private const val AUTO_CLOSE_SECONDS = 10

@Composable
fun BrewingScreen(
    preset: BrewPreset?,
    device: PairedDevice?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrewViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showForceQuit by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (preset == null) {
            Text("Preset not found", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClose) { Text("Back") }
            return@Column
        }

        Text(preset.name, style = MaterialTheme.typography.headlineMedium)
        Text(
            "${productName(preset)} · ${brewSummary(preset)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        when (val s = state) {
            BrewUiState.Idle -> {
                if (device == null) {
                    Text("No machine paired.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Make sure a cup is in place.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { viewModel.start(device, preset) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start brewing") }
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }

            BrewUiState.Connecting -> {
                CircularProgressIndicator()
                Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
            }

            is BrewUiState.Brewing -> {
                Text(s.phase, style = MaterialTheme.typography.titleMedium)
                if (s.percent != null) {
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (s.doneMl != null && s.totalMl != null) {
                        Text(
                            "${s.doneMl} / ${s.totalMl} ml",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    CircularProgressIndicator()
                }
                StopButton(
                    onStop = { viewModel.stop() },
                    onForceQuit = { showForceQuit = true },
                )
                Text(
                    "Hold to force-quit if it's stuck",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is BrewUiState.Done -> {
                Text(
                    s.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (s.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    textAlign = TextAlign.Center,
                )
                if (s.success) {
                    // Auto-return to the brew list after a short countdown; tap to leave now.
                    var secondsLeft by remember { mutableStateOf(AUTO_CLOSE_SECONDS) }
                    LaunchedEffect(Unit) {
                        while (secondsLeft > 0) {
                            delay(1_000)
                            secondsLeft--
                        }
                        onClose()
                    }
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("Done (%02d)".format(secondsLeft))
                    }
                } else {
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }

            is BrewUiState.Failed -> {
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }

    if (showForceQuit) {
        AlertDialog(
            onDismissRequest = { showForceQuit = false },
            title = { Text("Force-quit brewing?") },
            text = {
                Text(
                    "This closes the app's connection and returns to the list without " +
                        "contacting the machine — the machine keeps doing whatever it's doing. " +
                        "Use this only if the screen is stuck.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showForceQuit = false
                    viewModel.forceQuit()
                    onClose()
                }) { Text("Force-quit") }
            },
            dismissButton = {
                TextButton(onClick = { showForceQuit = false }) { Text("Cancel") }
            },
        )
    }
}

/** Outlined "Stop" control: tap to stop normally, long-press to force-quit. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopButton(onStop: () -> Unit, onForceQuit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .combinedClickable(onClick = onStop, onLongClick = onForceQuit)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Stop",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun productName(preset: BrewPreset): String =
    Ef1030Catalog.byCode(preset.productCode)?.name ?: "Product 0x%02X".format(preset.productCode)

private fun brewSummary(preset: BrewPreset): String {
    val product = Ef1030Catalog.byCode(preset.productCode)
    val parts = mutableListOf<String>()
    if (product == null || product.hasWater) parts += "${preset.waterMl} ml"
    if (product == null || product.hasStrength) parts += "strength ${preset.strength}"
    preset.milkMl?.let { parts += "milk $it" }
    preset.bypassMl?.let { parts += "bypass $it ml" }
    return parts.joinToString(" · ")
}
