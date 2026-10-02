package tech.kloos.kompound.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
class KShakeTest {
    private fun Color.isRed() = abs(red - 1f) < 0.1f && green < 0.1f && blue < 0.1f
    private fun ImageBitmap.redAt(x: Int, y: Int = 25) = toPixelMap()[x, y].isRed()

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: KShakeState) = setContent {
        Box(Modifier.testTag("host").size(200.dp, 50.dp)) {
            Box(Modifier.offset(x = 60.dp).size(50.dp).shake(state).background(Color.Red))
        }
    }

    @Test
    fun nothingMovesUntilItIsShaken() = runComposeUiTest {
        val state = KShakeState()
        show(state)
        mainClock.advanceTimeBy(500)
        val image = onNodeWithTag("host").captureToImage()
        assertTrue(image.redAt(70) && !image.redAt(50) && !image.redAt(125))
    }

    @Test
    fun shakeMovesTheDrawnElementAndThenSettlesBack() = runComposeUiTest {
        val state = KShakeState()
        show(state)
        mainClock.autoAdvance = false
        state.shake(KShakeSpec(translateX = 20.dp, swings = 4, swingMillis = 100))
        mainClock.advanceTimeBy(100)       // first swing done: moved 20 to the right
        val moved = onNodeWithTag("host").captureToImage()
        assertTrue(moved.redAt(125) && !moved.redAt(65), "not shifted right")
        mainClock.advanceTimeBy(2_000)
        val rest = onNodeWithTag("host").captureToImage()
        assertTrue(rest.redAt(70) && !rest.redAt(125) && !rest.redAt(50), "did not settle at the origin")
    }

    @Test
    fun anotherShakeRestartsAnAnimationInProgress() = runComposeUiTest {
        val state = KShakeState()
        show(state)
        mainClock.autoAdvance = false
        state.shake(KShakeSpec(translateX = 20.dp, swings = 6, swingMillis = 100))
        mainClock.advanceTimeBy(150)
        state.shake(KShakeSpec(translateX = 20.dp, swings = 2, swingMillis = 100))
        mainClock.advanceTimeBy(3_000)
        val rest = onNodeWithTag("host").captureToImage()
        assertTrue(rest.redAt(70) && !rest.redAt(125), "stuck away from the origin")
    }

    @Test
    fun layoutBoundsDoNotChangeWhileShaking() = runComposeUiTest {
        val state = KShakeState()
        setContent { Box(Modifier.testTag("t").size(40.dp).shake(state)) }
        val before = onNodeWithTag("t").fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        state.shake(KShakeSpec.Wobble)
        mainClock.advanceTimeBy(60)
        assertEquals(before, onNodeWithTag("t").fetchSemanticsNode().boundsInRoot)
    }
}
