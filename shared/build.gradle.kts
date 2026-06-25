import automatl.juras.gradle.GenerateBuildMetadata
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val generatedBuildMetadataDir = layout.buildDirectory.dir("generated/buildMetadata/commonMain/kotlin")
val generateBuildMetadata = tasks.register<GenerateBuildMetadata>("generateBuildMetadata") {
    outputDir.set(generatedBuildMetadataDir)
    appVersion.set(providers.gradleProperty("juras.versionName"))
    rootDirectory.set(rootProject.layout.projectDirectory)
    outputs.upToDateWhen { false }
}

android {
    namespace = "automatl.juras.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":protocol"))

            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            // JetBrains KMP lifecycle + navigation (same androidx.* packages, multiplatform)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation.compose)

            // Domain + serialization
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kaml)

            // Drag-to-reorder (KMP compatible v2.x)
            implementation(libs.reorderable)
        }
        commonMain.get().kotlin.srcDir(generateBuildMetadata)
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.activity.compose)
        }
    }
}
