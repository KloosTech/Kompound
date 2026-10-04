plugins {
    kotlin("multiplatform") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("tech.kloos.kompound") version "0.1.0-SNAPSHOT"            // opts in to the Compose Styles API every Kompound call needs
    id("tech.kloos.kompound.demos") version "0.1.0-SNAPSHOT"   // generates KompoundRegistry_<moduleId> from @KompoundDemo
}

val kompoundVersion = "0.1.0-SNAPSHOT"

kotlin {
    jvm("desktop")
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation("tech.kloos.kompound:kompound:$kompoundVersion")
            implementation(compose.foundation)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
