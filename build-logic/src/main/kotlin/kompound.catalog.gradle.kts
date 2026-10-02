import tech.kloos.kompound.gradle.KompoundCatalogExtension

plugins { id("tech.kloos.kompound.catalog") }

extensions.configure<KompoundCatalogExtension> {
    demoApi.set(project(":kompound-demo"))
}
