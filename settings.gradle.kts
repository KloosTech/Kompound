pluginManagement {
    includeBuild("build-logic")
    includeBuild("kompound-gradle-plugin")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "Kompound"

include(
    ":kompound-annotations",
    ":kompound-demo",
    ":kompound-processor",
    ":kompound",
    ":showcase",
    ":catalog:shared",
    ":catalog:desktopApp",
    ":catalog:androidApp",
    ":catalog:webApp",
)
