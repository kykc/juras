package automatl.juras.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import automatl.juras.domain.BrewPreset

// Placeholder screen for Brewing (implemented in Step 8). Exists so navigation is
// wired end-to-end.

@Composable
fun BrewingScreen(preset: BrewPreset?, modifier: Modifier = Modifier) {
    Placeholder(
        title = "Brewing",
        body = buildString {
            append(preset?.name ?: "Unknown preset")
            append("\n\nBrewing is implemented in the next step.")
        },
        modifier = modifier,
    )
}

@Composable
private fun Placeholder(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
