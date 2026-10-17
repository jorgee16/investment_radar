plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.0"
}

group = "com.investmentradar"
version = "0.5.0"

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.investmentradar.desktop.MainKt"
        nativeDistributions {
            packageName = "Investment Radar"
            packageVersion = "0.5.0"
        }
    }
}
