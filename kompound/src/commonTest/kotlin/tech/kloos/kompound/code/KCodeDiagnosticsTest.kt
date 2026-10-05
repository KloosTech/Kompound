package tech.kloos.kompound.code

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KCodeDiagnosticsTest {
    @Test
    fun theMessageIsShownWithItsLineNumber() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                KCode("{\n  \"a\": ,\n}", language = KCodeLanguage.Json, diagnostics = listOf(KCodeDiagnostic(9, 10, "Value expected")))
            }
        }
        onNodeWithText("Line 2: Value expected").assertIsDisplayed()
    }

    @Test
    fun theRangeIsUnderlinedAndClampedToTheText() {
        val colors = KCodeColors.OneDark
        val text = highlight("abc", KCodeLanguage.Plain, colors, listOf(KCodeDiagnostic(1, 99, "x"), KCodeDiagnostic(-5, -5, "y")))
        val underlined = text.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }.map { it.start to it.end }
        assertEquals(listOf(1 to 3, 0 to 1), underlined)
    }
}
