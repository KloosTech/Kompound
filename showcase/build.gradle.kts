plugins { id("kompound.demos") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kompound"))
            implementation(project(":kompound-graph"))
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}
