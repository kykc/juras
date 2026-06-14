import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Pure-Kotlin library: the JURA wire protocol (cipher, framing, transport, commands).
// Intentionally has NO Android-specific dependencies so it stays unit-testable on a
// plain JVM and cross-checkable against ../jura.py.

android {
    namespace = "automatl.juras.protocol"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        named("main") {
            // commonMain/resources aren't picked up by AGP automatically in KMP library
            // modules — add them explicitly so getResourceAsStream works on Android.
            resources.srcDirs("src/commonMain/resources")
        }
    }
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        testRuns["test"].executionTask.configure { useJUnit() }
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}
