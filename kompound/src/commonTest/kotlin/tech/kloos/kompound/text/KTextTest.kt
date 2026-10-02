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
import kotlin.test.Test
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
