import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    android {
        namespace = "tech.kloos.kompound." + project.name.replace('-', '.')
        compileSdk = 37
        minSdk = 24
        compilations.configureEach {
            compileTaskProvider.configure { compilerOptions.jvmTarget.set(JvmTarget.JVM_11) }
        }
    }
    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    iosArm64()
    iosSimulatorArm64()
    if (providers.gradleProperty("kompound.wasm").orNull == "true") {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs { browser() }
    }

    // Plain Kotlin/JS (browsers without Wasm GC, projects created from the KMP wizard with a `js` target).
    if (providers.gradleProperty("kompound.js").orNull != "false") {
        js { browser() }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
