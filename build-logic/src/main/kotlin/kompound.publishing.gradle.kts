import com.vanniktech.maven.publish.MavenPublishBaseExtension

// Maven Central publishing. Credentials and signing keys come from Gradle properties / env
// (ORG_GRADLE_PROJECT_mavenCentralUsername, ..._mavenCentralPassword, ..._signingInMemoryKey, ...),
// so everything works without secrets (publishToMavenLocal, CI on PRs).
plugins { id("com.vanniktech.maven.publish") }

fun prop(name: String) = providers.gradleProperty(name).get()

extensions.configure<MavenPublishBaseExtension> {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()


    pom {
        name.set("Kompound ${project.name}")
        // lazy: modules set `description` after this plugin is applied
        this.description.set(provider { project.description ?: "Kompound ${project.name}" })
        inceptionYear.set("2026")
        url.set(prop("POM_URL"))
        licenses {
            license {
                name.set(prop("POM_LICENSE_NAME"))
                url.set(prop("POM_LICENSE_URL"))
                distribution.set(prop("POM_LICENSE_URL"))
            }
        }
        developers {
            developer {
                id.set(prop("POM_DEVELOPER_ID"))
                name.set(prop("POM_DEVELOPER_NAME"))
                url.set(prop("POM_DEVELOPER_URL"))
            }
        }
        scm {
            url.set(prop("POM_SCM_URL"))
            connection.set(prop("POM_SCM_CONNECTION"))
            developerConnection.set(prop("POM_SCM_DEV_CONNECTION"))
        }
    }
}
