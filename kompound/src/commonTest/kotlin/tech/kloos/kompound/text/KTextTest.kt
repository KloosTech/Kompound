package tech.kloos.kompound.text

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.remember
import kotlin.math.abs
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import tech.kloos.kompound.containsColor
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KTextTest {
    private fun ImageBitmap.contains(c: Color): Boolean {
        val m = toPixelMap()
        for (y in 0 until height) for (x in 0 until width) {
            val p = m[x, y]
            if (abs(p.red - c.red) < 0.1f && abs(p.green - c.green) < 0.1f && abs(p.blue - c.blue) < 0.1f && p.alpha > 0.9f) return true
        }
        return false
    }

    private val red = Color(0xFFFF0000)

    @Test
    fun inheritsContentColorFromStyledParent() = runComposeUiTest {
        setContent {
            val state = remember { MutableStyleState(null) }
            Box(Modifier.testTag("p").styleable(state, Style { contentColor(red); textStyle(androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp))) })) {
                KText("Hello")
            }
        }
        assertTrue(onNodeWithTag("p").captureToImage().contains(red), "text did not inherit parent contentColor")
    }

    @Test
    fun ownStyleOverridesInheritedColor() = runComposeUiTest {
        val blue = Color(0xFF0000FF)
        setContent {
            val state = remember { MutableStyleState(null) }
            Box(Modifier.testTag("p").styleable(state, Style { contentColor(red) })) {
                KText("Hello", style = Style { contentColor(blue) })
            }
        }
        val img = onNodeWithTag("p").captureToImage()
        assertTrue(img.contains(blue), "own style colour missing")
        assertTrue(!img.contains(red), "inherited colour should be overridden")
    }
}

@OptIn(ExperimentalTestApi::class)
class KTextChangingTextTest {
    @Test
    fun aTitleThatChangesKeepsTheInheritedTypography() {
        val none = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        runComposeUiTest {
            var title by androidx.compose.runtime.mutableStateOf("First")
            setContent { tech.kloos.kompound.theme.KompoundTheme { tech.kloos.kompound.scaffold.KTopBar(title, windowInsets = none) } }
            val before = onNodeWithText("First", useUnmergedTree = true).fetchSemanticsNode().size.height
            title = "Second"
            waitForIdle()
            val after = onNodeWithText("Second", useUnmergedTree = true).fetchSemanticsNode().size.height
            assertEquals(before, after, "the title lost its title-large style when its text changed")
            assertTrue(after > 20, "title-large is taller than body text (was $after)")
        }
    }

    @Test
    fun aButtonLabelThatChangesKeepsItsColour() {
        val scheme = androidx.compose.material3.lightColorScheme(primary = Color(0xFF0000FF), onPrimary = Color(0xFF00FF00))
        runComposeUiTest {
            var label by androidx.compose.runtime.mutableStateOf("AAAA")
            setContent {
                androidx.compose.material3.MaterialTheme(scheme) {
                    tech.kloos.kompound.buttons.KButton(onClick = {}, Modifier.testTag("b")) { KText(label) }
                }
            }
            label = "MMMM"
            waitForIdle()
            assertTrue(onNodeWithTag("b").captureToImage().containsColor(Color(0xFF00FF00)), "label colour after the text changed")
        }
    }

    @Test
    fun annotatedTextTakesTheExplicitTextStyleAndKeepsItsSpans() = runComposeUiTest {
        val styled = androidx.compose.ui.text.buildAnnotatedString {
            append("Aa ")
            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) { append("bold") }
        }
        setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    KText(styled, Modifier.testTag("big"), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 40.sp))
                    KText(styled, Modifier.testTag("small"))
                }
            }
        }
        assertTrue(onNodeWithTag("big").fetchSemanticsNode().size.height > onNodeWithTag("small").fetchSemanticsNode().size.height * 2)
        val shown = onNodeWithTag("big").fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)!!.single()
        assertTrue(shown.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
    }
}
