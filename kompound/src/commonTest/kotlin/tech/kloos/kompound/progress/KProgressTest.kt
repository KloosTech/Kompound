package tech.kloos.kompound.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.near
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KProgressTest {
    private val s = ButtonTestScheme

    @Test
    fun determinateLinearFillsTheGivenFraction() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Box(Modifier.width(200.dp)) { KLinearProgress(0.5f, Modifier.testTag("p")) }
            }
        }
        val img = onNodeWithTag("p").captureToImage().toPixelMap()
        assertEquals(200, img.width)
        assertTrue(img[50, 2].near(s.primary), "left half is the indicator")
        assertTrue(img[150, 2].near(s.surfaceContainerHighest), "right half is the track")
    }

    @Test
    fun linearExtremesAreAllTrackOrAllIndicator() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                androidx.compose.foundation.layout.Column(Modifier.width(100.dp)) {
                    KLinearProgress(0f, Modifier.testTag("zero"))
                    KLinearProgress(1f, Modifier.testTag("full"))
                    KLinearProgress(2f, Modifier.testTag("over"))   // coerced to 1
                }
            }
        }
        assertTrue(onNodeWithTag("zero").captureToImage().toPixelMap()[50, 2].near(s.surfaceContainerHighest))
        assertTrue(onNodeWithTag("full").captureToImage().toPixelMap()[98, 2].near(s.primary))
        assertTrue(onNodeWithTag("over").captureToImage().toPixelMap()[98, 2].near(s.primary))
    }

    @Test
    fun semanticsExposeProgressOrIndeterminate() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            MaterialTheme(s) {
                androidx.compose.foundation.layout.Column {
                    KLinearProgress(0.25f, Modifier.testTag("det"))
                    KLinearProgress(null, Modifier.testTag("ind"))
                    KCircularProgress(0.75f, Modifier.testTag("cdet"))
                }
            }
        }
        mainClock.advanceTimeByFrame()
        val det = onNodeWithTag("det").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!
        assertEquals(0.25f, det.current)
        assertEquals(0f..1f, det.range)
        assertEquals(ProgressBarRangeInfo.Indeterminate, onNodeWithTag("ind").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals(0.75f, onNodeWithTag("cdet").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.current)
    }

    @Test
    fun indeterminateLinearIndicatorMoves() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { MaterialTheme(s) { Box(Modifier.width(200.dp)) { KLinearProgress(null, Modifier.testTag("p")) } } }
        mainClock.advanceTimeByFrame()
        val before = onNodeWithTag("p").captureToImage().toPixelMap()
        mainClock.advanceTimeBy(700)
        val after = onNodeWithTag("p").captureToImage().toPixelMap()
        val countBefore = (0 until 200).count { before[it, 2].near(s.primary) }
        val firstBefore = (0 until 200).firstOrNull { before[it, 2].near(s.primary) }
        val firstAfter = (0 until 200).firstOrNull { after[it, 2].near(s.primary) }
        assertTrue(firstAfter != firstBefore || countBefore != (0 until 200).count { after[it, 2].near(s.primary) }, "indicator did not move")
    }

    @Test
    fun determinateCircularSweepsClockwiseFromTheTop() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                androidx.compose.foundation.layout.Column {
                    KCircularProgress(0.25f, Modifier.testTag("quarter"))
                    KCircularProgress(0.75f, Modifier.testTag("three"))
                }
            }
        }
        val q = onNodeWithTag("quarter").captureToImage().toPixelMap()
        assertEquals(40, q.width)
        assertTrue(q[20, 3].near(s.primary), "top is covered at 25%")
        assertTrue(q[37, 20].near(s.primary), "right is reached at 25%")
        assertTrue(q[3, 20].near(s.surfaceContainerHighest), "left is still track at 25%")
        assertTrue(q[20, 37].near(s.surfaceContainerHighest), "bottom is still track at 25%")
        val t = onNodeWithTag("three").captureToImage().toPixelMap()
        assertTrue(t[3, 20].near(s.primary), "left is covered at 75%")
        assertTrue(t[20, 37].near(s.primary), "bottom is covered at 75%")
    }

    @Test
    fun indeterminateCircularSpins() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { MaterialTheme(s) { KCircularProgress(null, Modifier.testTag("c")) } }
        mainClock.advanceTimeByFrame()
        val before = onNodeWithTag("c").captureToImage().toPixelMap()
        assertTrue(before[20, 3].near(s.primary), "arc starts at the top")
        mainClock.advanceTimeBy(300)
        val after = onNodeWithTag("c").captureToImage().toPixelMap()
        assertFalse(after[20, 3].near(s.primary), "arc has rotated away from the top")
    }

    @Test
    fun customColoursAndSizeAreApplied() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KCircularProgress(1f, Modifier.testTag("c"), size = 24.dp, color = androidx.compose.ui.graphics.Color.Magenta)
            }
        }
        val img = onNodeWithTag("c").captureToImage().toPixelMap()
        assertEquals(24, img.width)
        assertTrue(img[12, 2].near(androidx.compose.ui.graphics.Color.Magenta))
    }
}
