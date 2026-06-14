package automatl.juras

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import automatl.juras.data.FileAppStateStore
import automatl.juras.ui.JurasApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.image.BufferedImage

@Serializable
private data class WindowGeometry(
    val x: Float? = null,
    val y: Float? = null,
    val width: Float = 390f,
    val height: Float = 780f,
)

private val geometryJson = Json { ignoreUnknownKeys = true }
private val geometryFile get() = configDir().resolve("window_geometry.json")

private fun loadWindowState(): WindowState {
    val g = runCatching {
        geometryJson.decodeFromString<WindowGeometry>(geometryFile.readText())
    }.getOrDefault(WindowGeometry())
    val position = if (g.x != null && g.y != null)
        WindowPosition.Absolute(g.x.dp, g.y.dp)
    else
        WindowPosition.PlatformDefault
    return WindowState(position = position, size = DpSize(g.width.dp, g.height.dp))
}

// Read geometry from the AWT window directly — CMP doesn't reliably sync size/position
// back into WindowState, so reading from the native window is the ground truth.
private fun saveGeometry(awtWindow: java.awt.Window) {
    val g = WindowGeometry(
        x = awtWindow.x.toFloat(),
        y = awtWindow.y.toFloat(),
        width = awtWindow.width.toFloat(),
        height = awtWindow.height.toFloat(),
    )
    runCatching {
        geometryFile.parentFile?.mkdirs()
        geometryFile.writeText(geometryJson.encodeToString(g))
    }.onFailure { it.printStackTrace() }
}

fun main() {
    System.setProperty("apple.awt.application.name", "Juras")
    val awtIcon = appIcon()
    runCatching { Taskbar.getTaskbar().iconImage = awtIcon }

    val store = FileAppStateStore()
    val windowState = loadWindowState()
    val capturedWindow = java.util.concurrent.atomic.AtomicReference<java.awt.Window?>(null)

    // Cmd+Q / app-menu Quit on macOS sends applicationShouldTerminate, which does NOT
    // fire onCloseRequest. A shutdown hook covers all exit paths.
    Runtime.getRuntime().addShutdownHook(Thread {
        capturedWindow.get()?.let { saveGeometry(it) }
    })

    application {
        Window(
            onCloseRequest = {
                capturedWindow.get()?.let { saveGeometry(it) }
                exitApplication()
            },
            title = "Juras",
            icon = BitmapPainter(awtIcon.toComposeImageBitmap()),
            state = windowState,
        ) {
            SideEffect { capturedWindow.set(window) }
            JurasApp(store = store)
        }
    }
}

private fun appIcon(): BufferedImage {
    val sz = 512
    val img = BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val u = sz / 8

    g.color = Color(0x6650A4)
    g.fillRoundRect(0, 0, sz, sz, u, u)

    g.color = Color.WHITE
    g.fillRoundRect(u * 2, u * 3, u * 4, u * 4, u / 3, u / 3)

    g.stroke = BasicStroke(u / 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.drawArc(u * 5, u * 4, u + u / 2, u * 2, 90, -180)

    g.stroke = BasicStroke(1f)
    g.fillRoundRect(u, u * 7 + u / 3, u * 6, u / 2, u / 4, u / 4)

    g.dispose()
    return img
}
