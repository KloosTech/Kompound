package tech.kloos.kompound.code

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KCodeShellLanguageTest {
    private fun kinds(code: String) = KCodeLanguage.Shell.tokenize(code).map { it.type to code.substring(it.start, it.end) }

    @Test
    fun theCommandStartingEachStatementIsAFunctionAndOptionsAndArgumentsAreNot() {
        val tokens = kinds("ls -la /tmp | grep foo && echo done")
        assertEquals(listOf("ls", "grep", "echo"), tokens.filter { it.first == KCodeTokenType.Function }.map { it.second })
        assertTrue(tokens.none { it.second == "-la" || it.second == "/tmp" || it.second == "foo" })
    }

    @Test
    fun commentsStringsAndVariables() {
        val tokens = kinds("echo \"hi \$USER\" 'raw \$X' \${HOME}/x # trailing\nFOO=1")
        assertTrue(KCodeTokenType.String to "\"hi \$USER\"" in tokens)
        assertTrue(KCodeTokenType.String to "'raw \$X'" in tokens, "no variables inside single quotes")
        assertTrue(KCodeTokenType.Property to "\${HOME}" in tokens)
        assertTrue(KCodeTokenType.Comment to "# trailing" in tokens)
        assertTrue(tokens.none { it.first == KCodeTokenType.Function && it.second.startsWith("FOO") }, "an assignment is not a command")
    }

    @Test
    fun hashInsideAWordIsNotAComment() {
        val tokens = kinds("echo a#b\necho \$#")
        assertTrue(tokens.none { it.first == KCodeTokenType.Comment })
        assertTrue(KCodeTokenType.Property to "\$#" in tokens)
    }

    @Test
    fun keywordsOfControlFlowAndTheCommandAfterThem() {
        val tokens = kinds("if [ -f x ]; then\n  cat x\nfi\nfor f in a b; do echo \$f; done")
        assertEquals(listOf("if", "then", "fi", "for", "in", "do", "done"), tokens.filter { it.first == KCodeTokenType.Keyword }.map { it.second })
        assertTrue(KCodeTokenType.Function to "cat" in tokens)
        assertTrue(KCodeTokenType.Function to "echo" in tokens)
    }

    @Test
    fun tokensStayInsideTheTextAndDoNotOverlapEvenOnOddInput() {
        for (code in listOf("", "'unterminated", "\"also \\", "\${no close", "\$(sub\n", "a && || ;; ! \$ \$\$ #", "echo é 日本 `x`")) {
            val tokens = KCodeLanguage.Shell.tokenize(code)
            var last = 0
            for (t in tokens) {
                assertTrue(t.start >= last && t.end <= code.length && t.start < t.end, "bad token $t in \"$code\"")
                last = t.end
            }
        }
    }

    @Test
    fun cLikeBuildsALanguageFromAKeywordSetAndTheLexingHelpersAreAvailable() {
        val java = KCodeLanguage.cLike(setOf("public", "static", "void"))
        val code = "public static void main(String[] a) { // hi\n}"
        val tokens = java.tokenize(code).map { it.type to code.substring(it.start, it.end) }
        assertEquals(listOf("public", "static", "void"), tokens.filter { it.first == KCodeTokenType.Keyword }.map { it.second })
        assertTrue(KCodeTokenType.Function to "main" in tokens)
        assertTrue(KCodeTokenType.Comment to "// hi" in tokens)
        assertEquals(7, KCodeLexing.quotedEnd("\"abc\" d", 0, '"').let { it + 2 })
        assertEquals(3, KCodeLexing.identEnd("ab_ c", 0))
        assertEquals(4, KCodeLexing.lineEnd("abcd\nx", 1))
        assertEquals(2, KCodeLexing.scanWhile("12ab", 0) { it.isDigit() })
    }
}

class TextFieldValueInsertTest {
    @Test
    fun insertingReplacesTheSelectionOrGoesAtTheCaretAndMovesTheCaretAfterIt() {
        assertEquals(TextFieldValue("echo \$X done", TextRange(7)), TextFieldValue("echo  done", TextRange(5)).insertAtCursor("\$X"))
        assertEquals(TextFieldValue("echo \$X", TextRange(7)), TextFieldValue("echo foo", TextRange(5, 8)).insertAtCursor("\$X"), "selection replaced")
        assertEquals(TextFieldValue("ab\$X", TextRange(4)), TextFieldValue("ab", TextRange(2)).insertAtCursor("\$X"))
        assertEquals(TextFieldValue("\$Xab", TextRange(2)), TextFieldValue("ab", TextRange(0)).insertAtCursor("\$X"))
        assertEquals(TextFieldValue("x\$Y", TextRange(3)), TextFieldValue("xab", TextRange(3, 1)).insertAtCursor("\$Y"), "a backwards selection works too")
    }
}

@OptIn(ExperimentalTestApi::class)
class KCodeValueTest {
    @Test
    fun aButtonOutsideTheEditorInsertsAtTheCaretAndTheEditorKeepsTheFocus() = runComposeUiTest {
        val focus = FocusRequester()
        var value by mutableStateOf(TextFieldValue("echo  end", TextRange(5)))
        setContent {
            MaterialTheme(lightColorScheme()) {
                Column {
                    KCode(value, { value = it }, Modifier.testTag("code"), language = KCodeLanguage.Shell, focusRequester = focus)
                    KButton({ value = value.insertAtCursor("\$INPUT"); focus.requestFocus() }, Modifier.testTag("insert")) { KText("insert") }
                }
            }
        }
        onNodeWithTag("insert").performClick()
        waitForIdle()
        assertEquals("echo \$INPUT end", value.text)
        assertEquals(TextRange(11), value.selection)
    }
}
