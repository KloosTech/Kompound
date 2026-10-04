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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        val desktopTest by getting { dependencies { implementation(compose.desktop.currentOs) } }
    }
}

description = "Node graph framework for Compose Multiplatform: canvas, draggable nodes with typed ports, edges, undo."
