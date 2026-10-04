plugins {
    alias(libs.plugins.kotlin.jvm)
    id("kompound.publishing")
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

// The KSP processor runs inside the consumer's Gradle daemon: compile for Java 11 so it loads on any JDK from 11 up.
kotlin {
    explicitApi()
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

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
