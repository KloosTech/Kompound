import tech.kloos.kompound.gradle.KompoundDemosExtension

// In-repo wiring: use local projects instead of published coordinates.
plugins {
    id("kompound.kmp-library")
    id("kompound.compose")
    id("tech.kloos.kompound.demos")
}

extensions.configure<KompoundDemosExtension> {
    processor.set(project(":kompound-processor"))
    annotations.set(project(":kompound-annotations"))
    demoApi.set(project(":kompound-demo"))
}
