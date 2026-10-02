import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> {
        compilerOptions {
            // Styles API is experimental (ADR 0001). Opt in module-wide.
            optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
        }
    }
}
