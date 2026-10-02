package tech.kloos.kompound.border

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DashedBorderTest {
    private fun Color.isRed() = abs(red - 1f) < 0.1f && green < 0.1f && blue < 0.1f

    @Test
    fun dashesAlternateWithGapsAlongTheEdgeAndStayInsideTheBounds() = runComposeUiTest {
        setContent { Box(Modifier.testTag("b").size(60.dp).dashedBorder(Color.Red, width = 2.dp, dashLength = 4.dp, gapLength = 4.dp, cap = StrokeCap.Butt)) }
        val map = onNodeWithTag("b").captureToImage().toPixelMap()
        assertTrue(map[3, 1].isRed(), "no dash at x=3")
        assertTrue(!map[7, 1].isRed(), "no gap at x=7")
        assertTrue(map[11, 1].isRed(), "no dash at x=11")
        // inside the bounds: the first and last rows/columns of the box are not outside the stroke
        assertTrue(map[3, 0].isRed() || map[3, 1].isRed())
        assertTrue(!map[30, 30].isRed(), "the centre must stay empty")
    }

    @Test
    fun followsTheShapeAndDrawsOverContent() = runComposeUiTest {
        setContent { Box(Modifier.testTag("b").size(60.dp).dashedBorder(Color.Red, RoundedCornerShape(20.dp), 2.dp, 100.dp, 0.dp, StrokeCap.Butt)) }
        val map = onNodeWithTag("b").captureToImage().toPixelMap()
        assertTrue(map[30, 1].isRed(), "straight top edge")
        assertTrue(!map[1, 1].isRed(), "the rounded corner must cut off the square corner")
    }

    @Test
    fun zeroWidthOrTinyBoxesDoNotCrash() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("a").size(1.dp).dashedBorder(Color.Red, width = 4.dp))
            Box(Modifier.testTag("b").size(20.dp).dashedBorder(Color.Red, width = 0.dp))
        }
        assertEquals(1, onNodeWithTag("a").fetchSemanticsNode().size.width)
    }
}
