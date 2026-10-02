package tech.kloos.kompound.code

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KCodeLanguageTest {
    private fun kinds(code: String, language: KCodeLanguage = KCodeLanguage.Kotlin) =
        language.tokenize(code).map { it.type to code.substring(it.start, it.end) }

    @Test
    fun kotlinKeywordsTypesAndFunctions() {
        val tokens = kinds("val x: String = foo(1)")
        assertTrue(KCodeTokenType.Keyword to "val" in tokens)
        assertTrue(KCodeTokenType.Type to "String" in tokens)
        assertTrue(KCodeTokenType.Function to "foo" in tokens)
        assertTrue(KCodeTokenType.Number to "1" in tokens)
        assertTrue(tokens.none { it.second == "x" }, "plain identifiers stay unstyled")
    }

    @Test
    fun keywordsInsideStringsAndCommentsAreNotKeywords() {
        val tokens = kinds("""val s = "fun val" // return""")
        assertEquals(1, tokens.count { it.first == KCodeTokenType.Keyword })
        assertTrue(KCodeTokenType.String to "\"fun val\"" in tokens)
        assertTrue(KCodeTokenType.Comment to "// return" in tokens)
    }

    @Test
    fun escapedQuoteDoesNotEndTheString() {
        assertTrue(KCodeTokenType.String to "\"a\\\"b\"" in kinds("\"a\\\"b\" val"))
    }

    @Test
    fun blockAndRawStringsMayContainNewlines() {
        val tokens = kinds("/* a\nb */ val s = \"\"\"x\ny\"\"\"")
        assertTrue(KCodeTokenType.Comment to "/* a\nb */" in tokens)
        assertTrue(KCodeTokenType.String to "\"\"\"x\ny\"\"\"" in tokens)
    }

    @Test
    fun annotationsAndNumbers() {
        val tokens = kinds("@Composable fun f() = 0x1F + 3.5f")
        assertTrue(KCodeTokenType.Annotation to "@Composable" in tokens)
        assertTrue(KCodeTokenType.Number to "0x1F" in tokens)
        assertTrue(KCodeTokenType.Number to "3.5f" in tokens)
    }

    @Test
    fun unterminatedLiteralsEndAtTheLineOrTheText() {
        assertEquals(KCodeTokenType.String to "\"abc", kinds("\"abc\nval").first())
        assertEquals(KCodeTokenType.Comment to "/* open", kinds("/* open").single())
    }

    @Test
    fun tokensStayInsideTheTextAndDoNotOverlap() {
        val code = "package a.b\n\n@Suppress(\"x\")\nclass K<T>(val n: Int = 1) { fun f(): T? = null /* c */ }\n'c' \"\"\"r\"\"\" \\ \$ ` é"
        for (language in listOf(KCodeLanguage.Kotlin, KCodeLanguage.Json, KCodeLanguage.Plain)) {
            var previousEnd = 0
            for (t in language.tokenize(code)) {
                assertTrue(t.start >= previousEnd && t.end > t.start && t.end <= code.length, "bad token $t")
                previousEnd = t.end
            }
        }
    }

    @Test
    fun jsonKeysAreProperties() {
        val tokens = kinds("""{"a": [1, true, null, "s"], "b": -2.5e3}""", KCodeLanguage.Json)
        assertTrue(KCodeTokenType.Property to "\"a\"" in tokens)
        assertTrue(KCodeTokenType.Property to "\"b\"" in tokens)
        assertTrue(KCodeTokenType.String to "\"s\"" in tokens)
        assertTrue(KCodeTokenType.Keyword to "true" in tokens)
        assertTrue(KCodeTokenType.Keyword to "null" in tokens)
        assertTrue(KCodeTokenType.Number to "-2.5e3" in tokens)
    }

    @Test
    fun highlightingNeverChangesTheText() {
        val code = "fun main() { println(\"hi\") }"
        assertEquals(code, highlight(code, KCodeLanguage.Kotlin, KCodeColors.OneDark).text)
        assertEquals("", highlight("", KCodeLanguage.Kotlin, KCodeColors.OneDark).text)
    }

    @Test
    fun defaultPalettesAreReadableOnTheirBackground() {
        for ((name, scheme) in listOf("light" to lightColorScheme(), "dark" to darkColorScheme())) {
            var colors: KCodeColors? = null
            runComposeUiTest { setContent { MaterialTheme(scheme) { colors = KCodeDefaults.colors() } }; waitForIdle() }
            val c = colors!!
            for ((token, color) in listOf(
                "plain" to c.plain, "keyword" to c.keyword, "type" to c.type, "string" to c.string, "number" to c.number,
                "comment" to c.comment, "function" to c.function, "annotation" to c.annotation, "property" to c.property,
                "punctuation" to c.punctuation,
            )) {
                val ratio = contrast(color.compositeOnto(c.background), c.background)
                assertTrue(ratio >= 4.5f, "$name $token contrast $ratio")
            }
        }
    }

    private fun Color.compositeOnto(bg: Color) = compositeOver(bg)
}

@OptIn(ExperimentalTestApi::class)
class KCodeTest {
    private val scheme = lightColorScheme()
    private val colors = KCodeColors.OneDark

    @Test
    fun readOnlyByDefaultShowsTheTextAndIgnoresTyping() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KCode("val x = 1", Modifier.testTag("c"), colors = colors) } }
        val field = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        fun text() = field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals("val x = 1", text())
        runCatching { field.performTextInput("X") }
        waitForIdle()
        assertEquals("val x = 1", text())
    }

    @Test
    fun keywordsAreDrawnInTheKeywordColour() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KCode("val", Modifier.testTag("c"), colors = colors, showLineNumbers = false) } }
        assertTrue(onNodeWithTag("c").captureToImage().containsColor(colors.keyword, 0.08f), "no keyword colour")
        assertTrue(!onNodeWithTag("c").captureToImage().containsColor(colors.string, 0.05f), "unexpected string colour")
    }

    @Test
    fun plainLanguageUsesOnlyThePlainColour() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KCode("val x", Modifier.testTag("c"), language = KCodeLanguage.Plain, colors = colors, showLineNumbers = false) } }
        assertTrue(!onNodeWithTag("c").captureToImage().containsColor(colors.keyword, 0.05f), "plain text was highlighted")
    }

    @Test
    fun lineNumbersFollowTheNumberOfLines() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KCode("a\nb\nc\nd", colors = colors) } }
        onNodeWithTextExact(" 1\n 2\n 3\n 4".replace(" ", "")).assertExists()
    }

    @Test
    fun editingCallsBackWithTheNewText() = runComposeUiTest {
        var code by mutableStateOf("fun ")
        setContent { MaterialTheme(scheme) { KCode(code, Modifier.testTag("c"), onCodeChange = { code = it }, colors = colors) } }
        onNode(hasSetTextAction()).performTextInput("main")
        waitForIdle()
        assertTrue(code.contains("main"), "typed text missing: '$code'")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onNodeWithTextExact(text: String) =
        onNodeWithText(text, substring = false)
}
