package tech.kloos.kompound.gradle

/** `:showcase` -> `showcase`, `:catalog:shared` -> `catalog_shared`. Safe inside a Kotlin identifier. */
internal fun sanitizeModuleId(raw: String): String =
    raw.trim(':').replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "root" }

internal const val REGISTRY_PACKAGE = "tech.kloos.kompound.registry"
internal fun registryClassName(moduleId: String) = "KompoundRegistry_$moduleId"
