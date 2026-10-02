plugins {
    id("kompound.library")
    id("kompound.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            // M3 is the design-token source only (ColorScheme / Typography / Shapes), see ADR 0001.
            api(compose.material3)
            implementation(compose.animation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        val desktopTest by getting { dependencies { implementation(compose.desktop.currentOs) } }
    }
}

description = "Kompound UI components for Compose Multiplatform, styled with the Compose Styles API."
