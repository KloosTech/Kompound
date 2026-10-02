package tech.kloos.kompound.buttons

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KProgressButtonTest {
    private val scheme = lightColorScheme(primary = Color(0xFF0000FF), onPrimary = Color.White, surface = Color.White)

    private fun Color.near(o: Color, tol: Float = 0.08f) = abs(red - o.red) < tol && abs(green - o.green) < tol && abs(blue - o.blue) < tol
    private fun ImageBitmap.at(x: Int, y: Int) = toPixelMap()[x, y]

    @Test
    fun idleShowsTheTextAndClicks() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(scheme) { KProgressButton("Upload", null, { clicks++ }, Modifier.testTag("b")) } }
        onNodeWithText("Upload").assertExists()
        onNodeWithTag("b").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun idleLooksLikeAFilledButton() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KProgressButton("Upload", null, {}, Modifier.testTag("b").width(200.dp)) } }
        mainClock.advanceTimeBy(500)
        val image = onNodeWithTag("b").captureToImage()
        assertTrue(image.at(10, image.height / 2).near(scheme.primary), "idle button is not filled with the accent: ${image.at(10, image.height / 2)} size ${image.width}x${image.height}")
        assertTrue(image.at(image.width - 10, image.height / 2).near(scheme.primary))
    }

    @Test
    fun runningShowsThePercentageAndFillsProportionally() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KProgressButton("Upload", 0.4f, {}, Modifier.testTag("b").width(200.dp)) } }
        mainClock.advanceTimeBy(1000)
        onNodeWithText("40%").assertExists()
        val image = onNodeWithTag("b").captureToImage()
        val y = image.height - 6
        assertTrue(image.at(10, y).near(scheme.primary), "left part is not filled")
        assertTrue(!image.at(image.width - 14, y).near(scheme.primary, 0.3f), "right part is filled")
        // the boundary sits near 40 %
        val boundary = (12 until image.width).first { !image.at(it, y).near(scheme.primary, 0.2f) }
        assertTrue(abs(boundary - 80) < 14, "fill boundary at $boundary of ${image.width}")
    }

    @Test
    fun clicksAreIgnoredWhileRunningUnlessAllowed() = runComposeUiTest {
        var clicks = 0
        var allow by mutableStateOf(false)
        setContent { MaterialTheme(scheme) { KProgressButton("Go", 0.2f, { clicks++ }, Modifier.testTag("b"), disableWhileInProgress = !allow) } }
        onNodeWithTag("b").performClick()
        assertEquals(0, clicks)
        allow = true
        waitForIdle()
        onNodeWithTag("b").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun exposesProgressToSemantics() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KProgressButton("Go", 0.25f, {}, Modifier.testTag("b")) } }
        val info = onNodeWithTag("b").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        assertNotNull(info)
        assertEquals(0.25f, info.current)
    }

    @Test
    fun customProgressTextAndDisabledState() = runComposeUiTest {
        var enabled by mutableStateOf(true)
        var clicks = 0
        setContent { MaterialTheme(scheme) { KProgressButton("Go", 0.5f, { clicks++ }, Modifier.testTag("b"), enabled = enabled, disableWhileInProgress = false, progressText = { "Halfway: $it" }) } }
        onNodeWithText("Halfway: 50").assertExists()
        enabled = false
        waitForIdle()
        runCatching { onNodeWithTag("b").performClick() }
        assertEquals(0, clicks)
    }

    @Test
    fun startingATaskDoesNotDrainBackwards() = runComposeUiTest {
        var progress by mutableStateOf<Float?>(null)
        setContent { MaterialTheme(scheme) { KProgressButton("Go", progress, {}, Modifier.testTag("b").width(200.dp)) } }
        mainClock.advanceTimeBy(500)
        mainClock.autoAdvance = false
        progress = 0.05f
        mainClock.advanceTimeBy(16)
        val image = onNodeWithTag("b").captureToImage()
        val y = image.height - 6
        assertTrue(!image.at(image.width / 2, y).near(scheme.primary, 0.3f), "fill did not restart from empty")
    }
}
