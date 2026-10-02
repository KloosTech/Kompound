package tech.kloos.kompound.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.text.KText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KButtonTest {
    private val scheme = lightColorScheme(primary = Color(0xFF0000FF), onPrimary = Color.White)

    private fun Color.near(o: Color, tol: Float = 0.06f) =
        abs(red - o.red) < tol && abs(green - o.green) < tol && abs(blue - o.blue) < tol && abs(alpha - o.alpha) < tol

    /** Colour of a pixel inside the button but left of the label (pure background). */
    private fun ImageBitmap.backgroundSample(): Color = toPixelMap()[12, height / 2]

    private fun ImageBitmap.containsColor(c: Color): Boolean {
        val map = toPixelMap()
        for (y in 0 until height) for (x in 0 until width) if (map[x, y].near(c, 0.1f)) return true
        return false
    }

    @Test
    fun clickInvokesCallback() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(scheme) { KButton(onClick = { clicks++ }, modifier = Modifier.testTag("b")) { KText("Go") } } }
        onNodeWithTag("b").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledIgnoresClicksAndExposesState() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(scheme) { KButton(onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("b")) { KText("Go") } }
        }
        onNodeWithTag("b").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun hitAreaIncludesStylePadding() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KButton(onClick = {}, modifier = Modifier.testTag("b")) { KText("Go") } } }
        val size = onNodeWithTag("b").fetchSemanticsNode().size
        assertTrue(size.height >= 40, "button should be at least minHeight (40dp at density 1), was $size")
        assertTrue(size.width >= 48, "button should include horizontal padding, was $size")
    }

    @Test
    fun enabledButtonHasButtonRole() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KButton(onClick = {}, modifier = Modifier.testTag("b")) { KText("Go") } } }
        onNodeWithTag("b").assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun defaultStyleUsesThemePrimaryBackground() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KButton(onClick = {}, modifier = Modifier.testTag("b")) { KText("Go") } } }
        val sample = onNodeWithTag("b").captureToImage().backgroundSample()
        assertTrue(sample.near(scheme.primary), "expected primary, was $sample")
    }

    @Test
    fun consumerStyleOverridesOnlyWhatItSets() = runComposeUiTest {
        val red = Color(0xFFFF0000)
        setContent {
            MaterialTheme(scheme) {
                KButton(onClick = {}, modifier = Modifier.testTag("b"), style = Style { background(red) }) { KText("Go") }
            }
        }
        val img = onNodeWithTag("b").captureToImage()
        assertTrue(img.backgroundSample().near(red), "background override not applied")
        // Default text colour (onPrimary = white) is inherited from the default style, not lost.
        assertTrue(img.containsColor(Color.White), "default content colour was not preserved")
    }

    @Test
    fun consumerPressedBlockOverridesDefaultPressed() = runComposeUiTest {
        val green = Color(0xFF00FF00)
        val source = MutableInteractionSource()
        setContent {
            MaterialTheme(scheme) {
                KButton(onClick = {}, modifier = Modifier.testTag("b"), interactionSource = source,
                    style = Style { pressed { background(green) } }) { KText("Go") }
            }
        }
        runOnIdle { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        assertTrue(onNodeWithTag("b").captureToImage().backgroundSample().near(green))
    }

    @Test
    fun defaultPressedStyleChangesBackground() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent { MaterialTheme(scheme) { KButton(onClick = {}, modifier = Modifier.testTag("b"), interactionSource = source) { KText("Go") } } }
        val before = onNodeWithTag("b").captureToImage().backgroundSample()
        runOnIdle { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        val after = onNodeWithTag("b").captureToImage().backgroundSample()
        assertFalse(before.near(after, 0.01f), "pressed state did not change the background")
    }

    @Test
    fun labelInheritsContentColorFromButtonStyle() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KButton(onClick = {}, modifier = Modifier.testTag("b")) { KText("Go") } } }
        assertTrue(onNodeWithTag("b").captureToImage().containsColor(scheme.onPrimary), "label is not onPrimary")
    }

    @Test
    fun pressDoesNotRecomposeContent() = runComposeUiTest {
        var contentRuns = 0
        val source = MutableInteractionSource()
        setContent {
            MaterialTheme(scheme) {
                KButton(onClick = {}, interactionSource = source) {
                    SideEffect { contentRuns++ }
                    Box(Modifier.size(1.dp))
                }
            }
        }
        waitForIdle()
        val before = contentRuns
        runOnIdle { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        assertEquals(before, contentRuns, "content recomposed on press")
    }
}
