package automatl.juras.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import automatl.juras.domain.AppState
import automatl.juras.domain.BrewPreset
import automatl.juras.protocol.Temperature
import automatl.juras.protocol.product.Ef1030Catalog

@Composable
fun BrewScreen(
    state: AppState,
    onBrew: (BrewPreset) -> Unit,
    onAddPreset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPreset) {
                Icon(Icons.Filled.Add, contentDescription = "Add preset")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Brew", style = MaterialTheme.typography.headlineMedium)

            val device = state.pairedDevice
            Text(
                if (device != null) "${device.displayName} · ${device.host}" else "No machine paired",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.presets.isEmpty()) {
                Text(
                    "No presets yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.presets.forEach { preset ->
                    PresetCard(preset = preset, onClick = { onBrew(preset) })
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: BrewPreset, onClick: () -> Unit) {
    val product = Ef1030Catalog.byCode(preset.productCode)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(preset.name, style = MaterialTheme.typography.titleMedium)
            Text(
                product?.name ?: "Product 0x%02X".format(preset.productCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(presetSummary(preset), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun presetSummary(preset: BrewPreset): String {
    val product = Ef1030Catalog.byCode(preset.productCode)
    val parts = mutableListOf("${preset.waterMl} ml")
    if (product == null || product.hasStrength) parts += "strength ${preset.strength}"
    parts += temperatureLabel(preset.temperature)
    preset.milkMl?.let { parts += "milk $it" }
    return parts.joinToString(" · ")
}

private fun temperatureLabel(temperature: Temperature): String = when (temperature) {
    Temperature.LOW -> "low temp"
    Temperature.NORMAL -> "normal temp"
    Temperature.HIGH -> "high temp"
}
