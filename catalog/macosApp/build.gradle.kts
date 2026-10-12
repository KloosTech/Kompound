plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// Native (non-JVM) macOS catalog for Apple Silicon. Run: ./gradlew :catalog:macosApp:runDebugExecutableMacosArm64
kotlin {
    macosArm64 {
        binaries.executable {
            entryPoint = "tech.kloos.kompound.catalog.macos.main"
        }
    }
    sourceSets {
        macosMain.dependencies {
            implementation(project(":catalog:shared"))
            implementation(compose.ui)
        }
    }
}
