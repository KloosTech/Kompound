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
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
