package automatl.juras.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberAboutInfo(): AboutInfo = remember {
    val javaVersion = System.getProperty("java.version").orEmpty()
    val javaVendor = System.getProperty("java.vendor").orEmpty()
    val osName = System.getProperty("os.name").orEmpty()
    val osVersion = System.getProperty("os.version").orEmpty()

    AboutInfo(
        appVersion = BuildMetadata.appVersion,
        commit = BuildMetadata.commit,
        runtime = "Desktop - JVM $javaVersion ($javaVendor) - $osName $osVersion",
    )
}
