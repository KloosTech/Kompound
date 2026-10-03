plugins {
    id("kompound.kmp-library")
    id("kompound.compose")
    id("kompound.catalog")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "KompoundCatalog"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kompound"))
            implementation(project(":showcase"))   // demo modules; aggregated automatically (ADR 0003)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.ui.backhandler)   // system back gesture: detail view -> list
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":kompound-graph"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        val desktopTest by getting { dependencies { implementation(compose.desktop.currentOs) } }
    }
}
