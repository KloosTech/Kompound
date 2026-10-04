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

// One switch for where the libraries go: ./gradlew publishLibraries -PpublishTo=local|central|release|both [-PVERSION_NAME=x.y.z]
//   local    ~/.m2 only (default; safe, no credentials needed)
//   central  upload to Maven Central, released by hand in the portal
//   release  upload and release to Maven Central automatically (immutable!)
//   both     local, then central
// See docs/RELEASING.md. scripts/publish.sh is a wrapper that also sets the version.
val publishTargets = mapOf(
    "local" to listOf("publishToMavenLocalAll"),
    "central" to listOf("publishToMavenCentralAll"),
    "release" to listOf("publishAndReleaseToMavenCentralAll"),
    "both" to listOf("publishToMavenLocalAll", "publishToMavenCentralAll"),
)
val publishTo = providers.gradleProperty("publishTo").orElse("local")
val target = publishTo.get()
val chosen = publishTargets[target]

// Fails before anything is built when the choice cannot work.
val preflightPublish = tasks.register("preflightPublish") {
    group = "publishing"
    description = "Checks -PpublishTo and, for Maven Central, credentials, signing key and a non-SNAPSHOT version."
    doFirst {
        require(chosen != null) { "-PpublishTo=$target is not one of ${publishTargets.keys.joinToString()}" }
        if (target != "local") {
            val missing = listOf("mavenCentralUsername", "mavenCentralPassword", "signingInMemoryKey").filter { !providers.gradleProperty(it).isPresent }
            require(missing.isEmpty()) {
                "Publishing to Maven Central needs ${missing.joinToString { "ORG_GRADLE_PROJECT_$it" }} (plus signingInMemoryKeyId and signingInMemoryKeyPassword), see docs/RELEASING.md"
            }
            val version = providers.gradleProperty("VERSION_NAME").get()
            require(!version.endsWith("SNAPSHOT")) { "Pass a real version for Maven Central: -PVERSION_NAME=0.1.0-alpha03 (got $version)" }
        }
    }
}
tasks.register("publishLibraries") {
    group = "publishing"
    description = "Publishes every artifact to the place chosen with -PpublishTo=local|central|release|both (default local)."
    dependsOn(preflightPublish)
    chosen?.forEach { dependsOn(it) }
}
// the check runs before anything is built, and with `both` local goes first so a broken artifact never reaches Central
chosen?.forEach { name -> tasks.named(name) { dependsOn(preflightPublish) } }
if (target == "both") tasks.named("publishToMavenCentralAll") { mustRunAfter("publishToMavenLocalAll") }

// The Gradle plugin and the KSP processor run inside the consumer's build, so they must not need a newer JDK than the consumer has.
tasks.register("verifyBytecodeTarget") {
    group = "verification"
    description = "Fails when the Gradle plugin or the KSP processor were compiled for a Java version above 11."
    dependsOn(":kompound-processor:jar")
    dependsOn(gradle.includedBuild("kompound-gradle-plugin").task(":jar"))
    doLast {
        val files = listOf(
            project(":kompound-processor").layout.buildDirectory.dir("libs").get().asFile,
            file("kompound-gradle-plugin/build/libs"),
        ).mapNotNull { dir ->
            // the jar that was just built (older versions may still lie around in build/libs)
            dir.listFiles { f -> f.name.endsWith(".jar") && !f.name.contains("sources") && !f.name.contains("javadoc") }?.maxByOrNull { it.lastModified() }
        }
        check(files.isNotEmpty()) { "no jars found to check" }
        for (jar in files) {
            java.util.zip.ZipFile(jar).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".class") && !it.name.startsWith("META-INF") } ?: return@use
                val header = zip.getInputStream(entry).use { it.readNBytes(8) }
                val major = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
                check(major <= 55) { "${jar.name} is compiled for class version $major (Java ${major - 44}); it must be 55 (Java 11) or lower" }
            }
        }
    }
}
