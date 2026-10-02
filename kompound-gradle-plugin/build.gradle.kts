import java.util.Properties

plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish") version libs.versions.vanniktech.get()
}

val rootProps = Properties().apply { file("../gradle.properties").inputStream().use(::load) }

group = "tech.kloos.kompound"
// Same version as the main build; CI overrides with -PVERSION_NAME for release tags.
version = providers.gradleProperty("VERSION_NAME").orElse(rootProps.getProperty("VERSION_NAME")).get()

tasks.jar { manifest { attributes("Implementation-Version" to project.version.toString()) } }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("demos") {
            id = "tech.kloos.kompound.demos"
            implementationClass = "tech.kloos.kompound.gradle.KompoundDemosPlugin"
        }
        register("catalog") {
            id = "tech.kloos.kompound.catalog"
            implementationClass = "tech.kloos.kompound.gradle.KompoundCatalogPlugin"
        }
    }
}

description = "Gradle plugins that wire Kompound demo discovery (KSP) and the catalog aggregate registry."

// Same POM as the main build (see build-logic/kompound.publishing). Duplicated because this build
// is a dependency of build-logic and cannot use it.
mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    pom {
        name.set("Kompound kompound-gradle-plugin")
        description.set(provider { project.description })
        inceptionYear.set("2026")
        url.set("https://github.com/KloosTech/Kompound")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers { developer { id.set("kloostech"); name.set("Kloos Tech"); url.set("https://kloos.tech") } }
        scm {
            url.set("https://github.com/KloosTech/Kompound")
            connection.set("scm:git:git://github.com/KloosTech/Kompound.git")
            developerConnection.set("scm:git:ssh://git@github.com/KloosTech/Kompound.git")
        }
    }
}
