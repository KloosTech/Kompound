plugins {
    id("kompound.library")
    id("kompound.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kompound"))
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

description = "Node graph framework for Compose Multiplatform: canvas, draggable nodes with typed ports, edges, undo."
