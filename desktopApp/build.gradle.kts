import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

// --- Icon generation ---------------------------------------------------------

val generateMacOsIcon by tasks.registering {
    val icnsOut = layout.buildDirectory.file("generated-icon/Juras.icns")
    outputs.file(icnsOut)
    notCompatibleWithConfigurationCache("uses AWT image generation")
    onlyIf { System.getProperty("os.name")?.lowercase()?.contains("mac") == true }
    doLast {
        fun draw(size: Int): BufferedImage {
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val u = size / 8
            g.color = Color(0x6650A4)
            g.fillRoundRect(0, 0, size, size, u, u)
            g.color = Color.WHITE
            g.fillRoundRect(u * 2, u * 3, u * 4, u * 4, u / 3, u / 3)
            g.stroke = BasicStroke(u / 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawArc(u * 5, u * 4, u + u / 2, u * 2, 90, -180)
            g.stroke = BasicStroke(1f)
            g.fillRoundRect(u, u * 7 + u / 3, u * 6, u / 2, u / 4, u / 4)
            g.dispose()
            return img
        }

        val iconsetDir = layout.buildDirectory.dir("generated-icon/Juras.iconset").get().asFile
        iconsetDir.mkdirs()

        fun write(size: Int, name: String) =
            ImageIO.write(draw(size), "PNG", iconsetDir.resolve(name))

        write(16,   "icon_16x16.png")
        write(32,   "icon_16x16@2x.png")
        write(32,   "icon_32x32.png")
        write(64,   "icon_32x32@2x.png")
        write(128,  "icon_128x128.png")
        write(256,  "icon_128x128@2x.png")
        write(256,  "icon_256x256.png")
        write(512,  "icon_256x256@2x.png")
        write(512,  "icon_512x512.png")
        write(1024, "icon_512x512@2x.png")

        providers.exec {
            commandLine(
                "iconutil", "-c", "icns",
                iconsetDir.absolutePath,
                "--output", icnsOut.get().asFile.absolutePath,
            )
        }.result.get()
    }
}

afterEvaluate {
    tasks.named("createDistributable") { dependsOn(generateMacOsIcon) }
    tasks.named("packageDmg") { dependsOn(generateMacOsIcon) }
}

// -----------------------------------------------------------------------------

compose.desktop {
    application {
        mainClass = "automatl.juras.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Juras"
            packageVersion = "1.0.0"
            description = "JURA coffee machine controller"
            macOS {
                bundleID = "automatl.juras"
                iconFile = layout.buildDirectory.file("generated-icon/Juras.icns").get().asFile
            }
        }
    }
}
