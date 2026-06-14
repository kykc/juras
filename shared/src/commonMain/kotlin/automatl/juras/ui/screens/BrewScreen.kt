package automatl.juras.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import automatl.juras.domain.AppState
import automatl.juras.domain.BrewPreset
import automatl.juras.protocol.MachineCatalog
import automatl.juras.protocol.Temperature
import juras.shared.generated.resources.Res
import juras.shared.generated.resources.ic_drag_indicator
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun BrewScreen(
    state: AppState,
    catalog: MachineCatalog,
    onBrew: (BrewPreset) -> Unit,
    onEdit: (BrewPreset) -> Unit,
    onAddPreset: () -> Unit,
    onQuickBrew: () -> Unit,
    onReorder: (List<BrewPreset>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visiblePresets = remember(state.presets, catalog.modelId) {
        state.presets.filter { it.model == catalog.modelId }
    }
    val hiddenPresets = remember(state.presets, catalog.modelId) {
        state.presets.filter { it.model != catalog.modelId }
    }
    val hiddenCount = hiddenPresets.size
    var items by remember { mutableStateOf(visiblePresets) }
    LaunchedEffect(visiblePresets) { items = visiblePresets }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = items.indexOfFirst { it.id == from.key }
        val toIndex = items.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPreset) {
                Icon(Icons.Filled.Add, contentDescription = "Add preset")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Brew", style = MaterialTheme.typography.headlineMedium)
                        TextButton(onClick = onQuickBrew) { Text("Quick brew") }
                    }
                    val device = state.pairedDevice
                    Text(
                        if (device != null) {
                            "${device.displayName} · ${device.host}"
                        } else {
                            "No machine paired"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hiddenCount > 0) {
                        Text(
                            "$hiddenCount preset${if (hiddenCount == 1) "" else "s"} for other machines hidden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (items.isEmpty()) {
                        Text(
                            "No presets yet. Tap + to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(items, key = { it.id }) { preset ->
                ReorderableItem(reorderState, key = preset.id) { isDragging ->
                    PresetCard(
                        preset = preset,
                        catalog = catalog,
                        dragging = isDragging,
                        onEdit = { onEdit(preset) },
                        onBrew = { onBrew(preset) },
                        dragHandle = {
                            val haptics = LocalHapticFeedback.current
                            IconButton(
                                onClick = {},
                                modifier = Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = { onReorder(items + hiddenPresets) },
                                ),
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_drag_indicator),
                                    contentDescription = "Hold to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    preset: BrewPreset,
    catalog: MachineCatalog,
    dragging: Boolean,
    onEdit: () -> Unit,
    onBrew: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val product = catalog.productByCode(preset.productCode)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onEdit),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (dragging) 8.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dragHandle()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    product?.name ?: "Product 0x%02X".format(preset.productCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(presetSummary(preset, catalog), style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick = onBrew) { Text("Brew") }
        }
    }
}

private fun presetSummary(preset: BrewPreset, catalog: MachineCatalog): String {
    val product = catalog.productByCode(preset.productCode)
    val parts = mutableListOf<String>()
    if (product == null || product.hasWater) parts += "${preset.waterMl} ml"
    if (product == null || product.hasStrength) parts += "strength ${preset.strength}"
    if (product == null || product.hasTemperature) parts += temperatureLabel(preset.temperature)
    preset.milkMl?.let { parts += "milk $it" }
    preset.bypassMl?.let { parts += "bypass $it ml" }
    return parts.joinToString(" · ")
}

private fun temperatureLabel(temperature: Temperature): String = when (temperature) {
    Temperature.LOW -> "low temp"
    Temperature.NORMAL -> "normal temp"
    Temperature.HIGH -> "high temp"
}
