plugins {
    alias(libs.plugins.kotlin.jvm)
    id("kompound.publishing")
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin { explicitApi() }

dependencies { implementation(libs.ksp.api) }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kctfork.ksp)
    testImplementation(libs.compose.runtime.desktop)
    testImplementation(project(":kompound-annotations"))
    testImplementation(project(":kompound-demo"))
}

tasks.test {
    // kotlin-compile-testing needs reflective access to compiler internals on newer JDKs.
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
}

description = "KSP processor that generates a Kompound demo registry from @KompoundDemo functions."
