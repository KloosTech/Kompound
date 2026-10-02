package tech.kloos.kompound.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier

private const val ANNOTATION = "tech.kloos.kompound.annotations.KompoundDemo"
private const val COMPOSABLE = "androidx.compose.runtime.Composable"
private const val DEMO_SCOPE = "tech.kloos.kompound.demo.DemoScope"
private const val REGISTRY_PACKAGE = "tech.kloos.kompound.registry"
private const val MAX_DESCRIPTION = 160
private val ID_REGEX = Regex("[a-z0-9]+([._-][a-z0-9]+)*")

public class KompoundProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = KompoundProcessor(environment)
}

private class Demo(
    val fn: KSFunctionDeclaration,
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String>,
    val since: String,
    val status: String,
    val platforms: List<String>,
    val aliases: List<String>,
    val withScope: Boolean,
)

internal class KompoundProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {
    private var done = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (done) return emptyList()
        val moduleId = env.options["kompound.moduleId"] ?: "module"
        val functions = resolver.getSymbolsWithAnnotation(ANNOTATION).filterIsInstance<KSFunctionDeclaration>().toList()
        if (functions.isEmpty()) return emptyList()
        done = true

        val demos = functions.mapNotNull { parse(it) }
        val seen = mutableMapOf<String, KSFunctionDeclaration>()
        for (d in demos) {
            (listOf(d.id) + d.aliases).forEach { id ->
                val prev = seen.put(id, d.fn)
                if (prev != null) env.logger.error("Duplicate demo id or alias '$id' (also used by ${prev.qualifiedName?.asString()})", d.fn)
            }
        }
        if (demos.size != functions.size) return emptyList()

        val source = render(moduleId, demos.sortedBy { it.id })
        env.codeGenerator.createNewFile(
            Dependencies(aggregating = true, *functions.mapNotNull { it.containingFile }.toTypedArray()),
            REGISTRY_PACKAGE,
            "KompoundRegistry_$moduleId",
        ).use { it.write(source.toByteArray()) }
        return emptyList()
    }

    private fun parse(fn: KSFunctionDeclaration): Demo? {
        val name = fn.qualifiedName?.asString() ?: fn.simpleName.asString()
        fun fail(msg: String): Demo? { env.logger.error("@KompoundDemo $name: $msg", fn); return null }

        if (fn.parentDeclaration != null) return fail("must be a top-level function")
        if (fn.annotations.none { it.annotationType.resolve().declaration.qualifiedName?.asString() == COMPOSABLE }) {
            return fail("must be annotated @Composable")
        }
        if (fn.parameters.isNotEmpty()) return fail("must not have parameters")
        if (Modifier.PRIVATE in fn.modifiers) return fail("must not be private")
        val receiver = fn.extensionReceiver?.resolve()?.declaration?.qualifiedName?.asString()
        if (fn.extensionReceiver != null && receiver != DEMO_SCOPE) return fail("only a $DEMO_SCOPE extension receiver is allowed")

        val ann = fn.annotations.first { it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION }
        val args = ann.arguments.associate { it.name!!.asString() to it.value }
        fun str(key: String) = args[key] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        fun list(key: String) = (args[key] as? List<String>).orEmpty()

        val id = str("id")
        if (!ID_REGEX.matches(id)) return fail("id '$id' must match ${ID_REGEX.pattern}")
        val title = str("title")
        if (title.isBlank()) return fail("title must not be blank")
        val description = str("description")
        if (description.length > MAX_DESCRIPTION) return fail("description longer than $MAX_DESCRIPTION characters")
        val tags = list("tags").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

        return Demo(fn, id, title, description, str("category").ifBlank { "Utilities" }, tags, str("since"),
            str("status").ifBlank { "Stable" }, list("platforms"), list("aliases"), withScope = receiver != null)
    }

    private fun render(moduleId: String, demos: List<Demo>): String {
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$").replace("\n", "\\n") + "\""
        fun l(items: List<String>) = if (items.isEmpty()) "emptyList()" else items.joinToString(prefix = "listOf(", postfix = ")") { q(it) }
        val aliased = demos.mapIndexed { i, d -> d to "kompoundDemo$i" }
        val imports = aliased.joinToString("\n") { (d, alias) -> "import ${d.fn.qualifiedName!!.asString()} as $alias" }
        val entries = aliased.joinToString("\n") { (d, alias) ->
            val call = "$alias()"
            """
            |        DemoEntry(
            |            moduleId = ${q(moduleId)},
            |            meta = DemoMeta(
            |                id = ${q(d.id)}, title = ${q(d.title)}, description = ${q(d.description)},
            |                category = ${q(d.category)}, tags = ${l(d.tags)}, since = ${q(d.since)},
            |                status = ${q(d.status)}, platforms = ${l(d.platforms)}, aliases = ${l(d.aliases)},
            |            ),
            |            content = { $call },
            |        ),""".trimMargin()
        }
        return """
            |package $REGISTRY_PACKAGE
            |
            |import tech.kloos.kompound.demo.DemoEntry
            |import tech.kloos.kompound.demo.DemoMeta
            |import tech.kloos.kompound.demo.DemoRegistry
            |$imports
            |
            |/** Generated by kompound-processor. Do not edit. */
            |public object KompoundRegistry_$moduleId : DemoRegistry {
            |    override val moduleId: String = ${q(moduleId)}
            |    override val entries: List<DemoEntry> = listOf(
            |$entries
            |    )
            |}
            |""".trimMargin()
    }
}
