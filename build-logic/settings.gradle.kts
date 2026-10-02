dependencyResolutionManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
includeBuild("../kompound-gradle-plugin")
rootProject.name = "build-logic"
