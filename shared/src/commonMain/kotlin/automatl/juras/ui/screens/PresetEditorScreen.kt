package automatl.juras.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import automatl.juras.domain.BrewPreset
import automatl.juras.protocol.Temperature
import automatl.juras.protocol.product.Ef1030Catalog
import automatl.juras.protocol.product.Product
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Customization screen, used in two modes by which callbacks are supplied:
 * - **Edit/add a preset:** pass [onSave] (+ [onDelete] for existing).
 * - **Quick brew (one-time, not saved):** pass [onBrewNow] only.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun PresetEditorScreen(
    preset: BrewPreset?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: ((BrewPreset) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onBrewNow: ((BrewPreset) -> Unit)? = null,
) {
    val fallback = remember(preset) {
        Ef1030Catalog.byCode(preset?.productCode ?: -1) ?: Ef1030Catalog.products.first()
    }

    var productCode by rememberSaveable { mutableStateOf(preset?.productCode ?: fallback.code) }
    var label by rememberSaveable { mutableStateOf(preset?.name ?: fallback.name) }
    var strength by rememberSaveable {
        mutableStateOf(preset?.strength ?: fallback.strength?.default ?: 8)
    }
    var water by rememberSaveable { mutableStateOf(preset?.waterMl ?: fallback.water?.default ?: 0) }
    var temperature by rememberSaveable {
        mutableStateOf(preset?.temperature ?: fallback.defaultTemperature ?: Temperature.NORMAL)
    }
    var milk by rememberSaveable { mutableStateOf(preset?.milkMl ?: fallback.milk?.default ?: 0) }
    var bypass by rememberSaveable { mutableStateOf(preset?.bypassMl ?: fallback.bypass?.default ?: 0) }

    val product = Ef1030Catalog.byCode(productCode) ?: fallback

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val title = when {
            onSave == null -> "Quick brew"
            preset == null -> "New preset"
            else -> "Edit preset"
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)

        if (onSave != null) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FieldLabel("Product")
        ProductDropdown(selected = product) { chosen ->
            if (label.isBlank() || label == product.name) label = chosen.name
            productCode = chosen.code
            chosen.strength?.let { strength = it.default }
            chosen.water?.let { water = it.default }
            chosen.defaultTemperature?.let { temperature = it }
            milk = chosen.milk?.default ?: 0
            bypass = chosen.bypass?.default ?: 0
        }

        product.strength?.let { range ->
            SliderRow(
                title = "Strength",
                valueText = strength.toString(),
                value = strength,
                min = range.min, max = range.max, step = range.step,
                onChange = { strength = it },
            )
        }

        product.water?.let { range ->
            SliderRow(
                title = "Water",
                valueText = "$water ml",
                value = water,
                min = range.min, max = range.max, step = range.step,
                onChange = { water = it },
            )
        }

        if (product.hasTemperature) {
            FieldLabel("Temperature")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Temperature.entries.forEach { option ->
                    FilterChip(
                        selected = temperature == option,
                        onClick = { temperature = option },
                        label = { Text(temperatureLabel(option)) },
                    )
                }
            }
        }

        product.milk?.let { range ->
            SliderRow(
                title = "Milk foam",
                valueText = milk.toString(),
                value = milk,
                min = range.min, max = range.max, step = range.step,
                onChange = { milk = it },
            )
        }

        product.bypass?.let { range ->
            SliderRow(
                title = "Bypass water",
                valueText = "$bypass ml",
                value = bypass,
                min = range.min, max = range.max, step = range.step,
                onChange = { bypass = it },
            )
        }

        val current: () -> BrewPreset = {
            BrewPreset(
                id = preset?.id ?: Uuid.random().toString(),
                name = label.ifBlank { product.name },
                productCode = productCode,
                strength = strength,
                waterMl = water,
                temperature = temperature,
                milkMl = if (product.hasMilk) milk else null,
                bypassMl = if (product.hasBypass) bypass else null,
            )
        }

        onBrewNow?.let { brew ->
            Button(
                onClick = { brew(current()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Brew") }
        }

        onSave?.let { save ->
            val saveModifier = Modifier.fillMaxWidth()
            if (onBrewNow != null) {
                OutlinedButton(onClick = { save(current()) }, modifier = saveModifier) {
                    Text("Save preset")
                }
            } else {
                Button(onClick = { save(current()) }, modifier = saveModifier) { Text("Save") }
            }
        }

        if (preset != null && onDelete != null) {
            OutlinedButton(
                onClick = { onDelete(preset.id) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete") }
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ProductDropdown(selected: Product, onSelect: (Product) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.name, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Ef1030Catalog.products.forEach { product ->
                DropdownMenuItem(
                    text = { Text(product.name) },
                    onClick = {
                        onSelect(product)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        val steps = ((max - min) / step - 1).coerceAtLeast(0)
        Slider(
            value = value.coerceIn(min, max).toFloat(),
            onValueChange = { onChange(snap(it, min, step)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = steps,
        )
    }
}

private fun snap(raw: Float, min: Int, step: Int): Int {
    val units = ((raw - min) / step).roundToInt()
    return min + units * step
}

private fun temperatureLabel(temperature: Temperature): String = when (temperature) {
    Temperature.LOW -> "Low"
    Temperature.NORMAL -> "Normal"
    Temperature.HIGH -> "High"
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
