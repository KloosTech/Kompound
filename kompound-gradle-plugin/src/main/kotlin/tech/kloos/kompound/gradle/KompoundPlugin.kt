package tech.kloos.kompound.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

internal const val StyleApiOptIn = "androidx.compose.foundation.style.ExperimentalFoundationStyleApi"

/**
 * Opts every Kotlin compilation of the module in to the Compose Styles API (`ExperimentalFoundationStyleApi`).
 *
 * Every Kompound composable has a `style: Style` parameter, and `Style` is still experimental in Compose, so the Kotlin compiler asks
 * each calling module to opt in, even for a call that passes no style. Apply this plugin instead of adding the opt-in by hand:
 *
 * ```kotlin
 * plugins { id("tech.kloos.kompound") version "<version>" }
 * ```
 *
 * It works with Kotlin Multiplatform, Kotlin/JVM and Android modules and does nothing else. The `tech.kloos.kompound.demos` plugin
 * applies it too.
 */
public class KompoundPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.optInToStyleApi()
}

internal fun Project.optInToStyleApi() {
    tasks.withType(KotlinCompilationTask::class.java).configureEach { compilerOptions.optIn.add(StyleApiOptIn) }
}
