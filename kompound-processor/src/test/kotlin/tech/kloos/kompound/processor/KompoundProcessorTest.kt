package tech.kloos.kompound.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
class KompoundProcessorTest {

    private fun compile(vararg sources: String, moduleId: String = "test"): JvmCompilationResult {
        val files = sources.mapIndexed { i, s -> SourceFile.kotlin("Demo$i.kt", s) }
        return KotlinCompilation().apply {
            this.sources = files
            inheritClassPath = true
            configureKsp {
                symbolProcessorProviders += KompoundProcessorProvider()
                processorOptions["kompound.moduleId"] = moduleId
            }
        }.compile()
    }

    private fun JvmCompilationResult.registry(): String =
        sourcesGeneratedBySymbolProcessor.single { it.name.startsWith("KompoundRegistry_") }.readText()

    private fun demo(
        fn: String = "ButtonDemo",
        id: String = "button.primary",
        extra: String = "",
        signature: String = "fun $fn()",
    ) = """
        package demo
        import androidx.compose.runtime.Composable
        import tech.kloos.kompound.annotations.KompoundDemo
        import tech.kloos.kompound.demo.DemoScope

        @KompoundDemo(id = "$id", title = "Button" $extra)
        @Composable
        $signature {}
    """.trimIndent()

    @Test
    fun generatesRegistryNamedAfterModule() {
        val result = compile(demo(), moduleId = "showcase")
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val src = result.registry()
        assertTrue("object KompoundRegistry_showcase : DemoRegistry" in src)
        assertTrue("package tech.kloos.kompound.registry" in src)
        assertTrue("id = \"button.primary\"" in src)
        assertTrue("moduleId = \"showcase\"" in src)
    }

    @Test
    fun usageSampleIsCarriedIntoTheRegistryEscaped() {
        val result = compile(demo(extra = ", usage = \"\"\"KButton(onClick = {}) { KText(\"Hi \${'$'}x\") }\"\"\""))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val src = result.registry()
        assertTrue("usage = \"KButton(onClick = {}) { KText(\\\"Hi \\\$x\\\") }\"" in src, src)
    }

    @Test
    fun registryCompilesAndListsAllEntries() {
        // Not loaded reflectively: without the Compose compiler plugin composable lambdas have a different runtime type.
        val result = compile(demo(), demo(fn = "ChipDemo", id = "chip.basic"), moduleId = "m")
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertEquals(2, Regex("""\bDemoEntry\(""").findAll(result.registry()).count())
    }

    @Test
    fun entriesAreSortedById() {
        val src = compile(demo(fn = "B", id = "z.last"), demo(fn = "A", id = "a.first")).registry()
        assertTrue(src.indexOf("a.first") < src.indexOf("z.last"))
    }

    @Test
    fun tagsAreNormalisedAndDeduplicated() {
        val src = compile(demo(extra = ", tags = [\" Button \", \"CTA\", \"button\", \"\"]")).registry()
        assertTrue("tags = listOf(\"button\", \"cta\")" in src, src)
    }

    @Test
    fun defaultsAreApplied() {
        val src = compile(demo()).registry()
        assertTrue("category = \"Utilities\"" in src)
        assertTrue("status = \"Stable\"" in src)
    }

    @Test
    fun escapesQuotesAndDollarSigns() {
        val src = compile(demo(extra = ", description = \"say \\\"hi\\\" for \\\$5\"")).registry()
        assertTrue("say \\\"hi\\\" for \\${'$'}5" in src || "\\\$5" in src, src)
        // and it must still compile
        val result = compile(demo(extra = ", description = \"say \\\"hi\\\" for \\\$5\""))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun acceptsDemoScopeReceiver() {
        val result = compile(demo(signature = "fun DemoScope.ScopedDemo()"))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun noDemosGeneratesNothing() {
        val result = compile("package demo\nfun nothing() {}")
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.sourcesGeneratedBySymbolProcessor.none { it.name.startsWith("KompoundRegistry_") })
    }

    @Test
    fun duplicateIdFailsBuild() {
        val result = compile(demo(fn = "A"), demo(fn = "B"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("Duplicate demo id or alias 'button.primary'" in result.messages, result.messages)
    }

    @Test
    fun aliasCollidingWithOtherIdFailsBuild() {
        val result = compile(demo(fn = "A"), demo(fn = "B", id = "button.new", extra = ", aliases = [\"button.primary\"]"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("Duplicate demo id or alias" in result.messages, result.messages)
    }

    @Test
    fun nonComposableFailsBuild() {
        val result = compile("""
            package demo
            import tech.kloos.kompound.annotations.KompoundDemo
            @KompoundDemo(id = "x.y", title = "X")
            fun NotComposable() {}
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("must be annotated @Composable" in result.messages, result.messages)
    }

    @Test
    fun parametersFailBuild() {
        val result = compile(demo(signature = "fun WithParam(x: Int)"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("must not have parameters" in result.messages, result.messages)
    }

    @Test
    fun invalidIdFailsBuild() {
        val result = compile(demo(id = "Bad Id!"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("must match" in result.messages, result.messages)
    }

    @Test
    fun blankTitleFailsBuild() {
        val result = compile("""
            package demo
            import androidx.compose.runtime.Composable
            import tech.kloos.kompound.annotations.KompoundDemo
            @KompoundDemo(id = "x.y", title = " ")
            @Composable fun Blank() {}
        """.trimIndent())
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("title must not be blank" in result.messages, result.messages)
    }

    @Test
    fun overlongDescriptionFailsBuild() {
        val result = compile(demo(extra = ", description = \"${"x".repeat(161)}\""))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("longer than 160" in result.messages, result.messages)
    }

    @Test
    fun privateFunctionFailsBuild() {
        val result = compile(demo(signature = "private fun Hidden()"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("must not be private" in result.messages, result.messages)
    }

    @Test
    fun foreignReceiverFailsBuild() {
        val result = compile(demo(signature = "fun String.Foreign()"))
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("extension receiver is allowed" in result.messages, result.messages)
    }
}
