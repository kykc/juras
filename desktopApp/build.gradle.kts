import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

// Android's R8 dead-strips unused icons from materialIconsExtended, but on desktop there is
// no such step. All icons used in this app (Add, Settings, Info, ArrowDropDown) are in core,
// so substitute the 36MB extended JAR with the 864KB core JAR at build time.
configurations.all {
    resolutionStrategy.dependencySubstitution {
        all {
            val req = requested
            if (req is ModuleComponentSelector &&
                req.group == "org.jetbrains.compose.material" &&
                req.module == "material-icons-extended-desktop") {
                useTarget(
                    "org.jetbrains.compose.material:material-icons-core-desktop:${req.version}",
                    "Replace extended icons (~36MB) with core-only (~864KB)",
                )
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "automatl.juras.MainKt"
        jvmArgs(
            "-Dapple.awt.application.appearance=system"
        )
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Juras"
            packageVersion = providers.gradleProperty("juras.desktopPackageVersion").get()
            description = "JURA coffee machine controller"
            modules("java.instrument", "jdk.unsupported")
            macOS {
                bundleID = "automatl.juras"
                iconFile = file("src/jvmMain/resources/Juras.icns")
            }
        }
    }
}
