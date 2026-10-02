plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}

// Publishes every artifact including the plugin build (an included build, not covered by `publish` of the root).
val publishedBuildTasks = listOf("publishToMavenLocal", "publishToMavenCentral", "publishAndReleaseToMavenCentral")
publishedBuildTasks.forEach { name ->
    tasks.register("${name}All") {
        group = "publishing"
        description = "Runs $name in all published modules and the Gradle plugin build."
        dependsOn(subprojects.filter { it.plugins.hasPlugin("com.vanniktech.maven.publish") || it.name.startsWith("kompound") }.map { "${it.path}:$name" })
        dependsOn(gradle.includedBuild("kompound-gradle-plugin").task(":$name"))
    }
}
