package automatl.juras.ui.platform

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAboutInfo(): AboutInfo {
    val context = LocalContext.current
    return remember(context) {
        val packageInfo = context.packageManager.packageInfo(context.packageName)
        val versionName = packageInfo.versionName ?: BuildMetadata.appVersion
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        AboutInfo(
            appVersion = "$versionName ($versionCode)",
            commit = BuildMetadata.commit,
            runtime = "Mobile - Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) - " +
                listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
        )
    }
}

private fun PackageManager.packageInfo(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, 0)
    }
