import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin/JVM library: the JURA wire protocol (cipher, framing, transport,
// commands). Intentionally has NO Android dependencies so it stays unit-testable
// on a plain JVM and cross-checkable against ../jura.py.

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
