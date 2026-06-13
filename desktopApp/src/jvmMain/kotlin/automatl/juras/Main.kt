package automatl.juras

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import automatl.juras.data.FileAppStateStore
import automatl.juras.ui.JurasApp
import automatl.juras.ui.theme.JurasTheme
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.image.BufferedImage

fun main() {
    System.setProperty("apple.awt.application.name", "Juras")
    val awtIcon = appIcon()
    runCatching { Taskbar.getTaskbar().iconImage = awtIcon }

    val store = FileAppStateStore()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Juras",
            icon = BitmapPainter(awtIcon.toComposeImageBitmap()),
            state = rememberWindowState(size = DpSize(390.dp, 780.dp)),
        ) {
            JurasTheme {
                JurasApp(store = store)
            }
        }
    }
}

private fun appIcon(): BufferedImage {
    val sz = 512
    val img = BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val u = sz / 8

    // Background
    g.color = Color(0x6650A4)
    g.fillRoundRect(0, 0, sz, sz, u, u)

    // Cup body
    g.color = Color.WHITE
    g.fillRoundRect(u * 2, u * 3, u * 4, u * 4, u / 3, u / 3)

    // Handle arc (right side of cup)
    g.stroke = BasicStroke(u / 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.drawArc(u * 5, u * 4, u + u / 2, u * 2, 90, -180)

    // Saucer
    g.stroke = BasicStroke(1f)
    g.fillRoundRect(u, u * 7 + u / 3, u * 6, u / 2, u / 4, u / 4)

    g.dispose()
    return img
}
