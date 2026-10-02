plugins {
    id("kompound.library")
    // no foundation dependency, so no Styles opt-in: use the compose plugins directly
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kompound-annotations"))
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        val desktopTest by getting { dependencies { implementation(compose.desktop.currentOs) } }
    }
}

description = "Runtime model (DemoEntry, DemoRegistry) used by generated Kompound catalog registries."
