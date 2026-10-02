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
    }
}

description = "Runtime model (DemoEntry, DemoRegistry) used by generated Kompound catalog registries."
