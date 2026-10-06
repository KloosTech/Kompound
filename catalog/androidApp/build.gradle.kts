plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "tech.kloos.kompound.catalog.android"
    compileSdk = 37
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "tech.kloos.kompound.catalog"
        minSdk = 24
        targetSdk = 37
        // The UI test harness (deep link, test tags as resource ids; ADR 0008) exists only in the `maestro` build type.
        buildConfigField("boolean", "HARNESS", "false")
        // CI passes the workflow run number so every release is newer than the last (Android refuses downgrades).
        versionCode = providers.gradleProperty("VERSION_CODE").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("VERSION_NAME").get().substringBefore('-')
    }
    // Release signing comes from env (set by the release workflow when secrets exist); otherwise debug-signed.
    val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath.isPresent) {
            create("release") {
                storeFile = file(keystorePath.get())
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystorePath.isPresent) "release" else "debug")
        }
        // For Maestro UI tests (ADR 0008): like release (optimised, not debuggable) but debug-signed, with the harness switched on, and installable
        // next to the published catalog (own application id).
        create("maestro") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".maestro"
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "HARNESS", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":catalog:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(compose.foundation)
    implementation(compose.ui)
}
