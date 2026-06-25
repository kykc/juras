package automatl.juras.ui.platform

import androidx.compose.runtime.Composable

data class AboutInfo(
    val appVersion: String,
    val commit: String,
    val runtime: String,
)

@Composable
expect fun rememberAboutInfo(): AboutInfo
