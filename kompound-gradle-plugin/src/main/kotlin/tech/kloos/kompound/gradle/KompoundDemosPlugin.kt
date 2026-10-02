package tech.kloos.kompound.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applied to every module that contains `@KompoundDemo` functions.
 * Wires KSP on commonMain metadata (ADR 0002) so a `KompoundRegistry_<moduleId>` is generated.
 */
public abstract class KompoundDemosExtension {
    /** Used in the generated registry name and as namespace of demo ids. */
    public abstract val moduleId: Property<String>

    /** Dependency notation for the processor. Defaults to the published artifact. */
    public abstract val processor: Property<Any>
    public abstract val annotations: Property<Any>
    public abstract val demoApi: Property<Any>
}

public class KompoundDemosPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        pluginManager.apply("com.google.devtools.ksp")
        val version = KompoundDemosPlugin::class.java.`package`.implementationVersion ?: DEFAULT_VERSION
        val ext = extensions.create<KompoundDemosExtension>("kompoundDemos").apply {
            moduleId.convention(sanitizeModuleId(if (project.path == ":") project.name else project.path))
            processor.convention("tech.kloos.kompound:kompound-processor:$version")
            annotations.convention("tech.kloos.kompound:kompound-annotations:$version")
            demoApi.convention("tech.kloos.kompound:kompound-demo:$version")
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kmp = extensions.getByType<KotlinMultiplatformExtension>()
            val generated = layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin")
            kmp.sourceSets.getByName("commonMain") { kotlin.srcDir(generated) }
            // Resolved after evaluation so convention plugins can still override the notations.
            afterEvaluate {
                kmp.sourceSets.getByName("commonMain").dependencies {
                    implementation(ext.annotations.get())
                    implementation(ext.demoApi.get())
                }
                dependencies { add("kspCommonMainMetadata", ext.processor.get()) }
                (extensions.getByName("ksp") as com.google.devtools.ksp.gradle.KspExtension)
                    .arg("kompound.moduleId", ext.moduleId.get())
            }
            // S1: every compile/ksp task must run after the metadata ksp task.
            tasks.configureEach {
                val n = name
                val isCompile = n.startsWith("compileKotlin") || n.startsWith("compile") && n.endsWith("KotlinAndroid") || n == "compileAndroidMain"
                val isKspTarget = n.startsWith("ksp") && n != "kspCommonMainKotlinMetadata"
                if ((isCompile || isKspTarget) && n != "compileKotlinMetadata" && n != "compileCommonMainKotlinMetadata") {
                    dependsOn("kspCommonMainKotlinMetadata")
                }
            }
        }
    }

    private companion object { const val DEFAULT_VERSION = "0.1.0-SNAPSHOT" }
}
