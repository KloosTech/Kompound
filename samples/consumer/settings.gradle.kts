// Standalone build that consumes Kompound the way an external project would.
// Run `./gradlew publishToMavenLocalAll` in the repo root first (or point at Maven Central once released).
pluginManagement {
    repositories { mavenLocal(); google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { mavenLocal(); google(); mavenCentral() }
}
rootProject.name = "kompound-consumer-sample"
