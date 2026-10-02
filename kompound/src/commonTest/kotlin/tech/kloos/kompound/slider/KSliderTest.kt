package tech.kloos.kompound.slider

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.near
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSliderTest {
    private val s = ButtonTestScheme.copy(surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFCCCCCC))

    @Test
    fun positionToValueAccountsForTheThumbRadiusAtBothEnds() {
        val range = 0f..100f
        assertEquals(0f, valueFromPosition(0f, 220f, 20f, range, 0, false))
        assertEquals(0f, valueFromPosition(10f, 220f, 20f, range, 0, false))
        assertEquals(50f, valueFromPosition(110f, 220f, 20f, range, 0, false))
        assertEquals(100f, valueFromPosition(210f, 220f, 20f, range, 0, false))
        assertEquals(100f, valueFromPosition(5000f, 220f, 20f, range, 0, false), "clamped")
        assertEquals(75f, valueFromPosition(110f, 220f, 20f, range, 0, true).let { 100f - 25f }, "sanity")
        assertEquals(100f, valueFromPosition(0f, 220f, 20f, range, 0, true), "rtl flips the direction")
    }

    @Test
    fun stepsSnapToTheNearestPosition() {
        val range = 0f..10f
        assertEquals(4f, snapToStep(3.7f, range, 4))
        assertEquals(0f, snapToStep(-3f, range, 4))
        assertEquals(10f, snapToStep(99f, range, 4))
        assertEquals(3.7f, snapToStep(3.7f, range, 0), "continuous")
        assertEquals(5f, snapToStep(4.4f, 0f..10f, 1), "one step in the middle: positions 0, 5, 10")
    }

    @Test
    fun semanticsExposeValueRangeAndAStepsAwareSetProgressAction() = runComposeUiTest {
        var value by mutableStateOf(0.3f)
        setContent { MaterialTheme(s) { KSlider(value, { value = it }, Modifier.testTag("sl"), steps = 4) } }
        val info = onNodeWithTag("sl").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!
        assertEquals(0.3f, info.current)
        assertEquals(0f..1f, info.range)
        assertEquals(4, info.steps)
        onNodeWithTag("sl").performSemanticsAction(SemanticsActions.SetProgress) { it(0.55f) }
        assertEquals(0.6f, value, 0.001f)   // snapped to the nearest of 0, 0.2, 0.4, 0.6, 0.8, 1
    }

    @Test
    fun tappingTheTrackSetsTheValueThere() = runComposeUiTest {
        var value by mutableStateOf(0f)
        var finished = 0
        setContent {
            MaterialTheme(s) { Box(Modifier.width(220.dp)) { KSlider(value, { value = it }, Modifier.testTag("sl"), valueRange = 0f..100f, onValueChangeFinished = { finished++ }) } }
        }
        onNodeWithTag("sl").performTouchInput { click(Offset(110f, 24f)) }
        assertEquals(50f, value, 0.5f)
        assertEquals(1, finished)
    }

    @Test
    fun draggingFollowsThePointerAndFinishesOnce() = runComposeUiTest {
        var value by mutableStateOf(0f)
        var finished = 0
        setContent {
            MaterialTheme(s) { Box(Modifier.width(220.dp)) { KSlider(value, { value = it }, Modifier.testTag("sl"), valueRange = 0f..100f, onValueChangeFinished = { finished++ }) } }
        }
        onNodeWithTag("sl").performTouchInput { swipe(Offset(30f, 24f), Offset(190f, 24f), durationMillis = 200) }
        assertEquals(90f, value, 1.5f)
        assertEquals(1, finished)
    }

    @Test
    fun keyboardMovesTheValueAndHomeEndJumpToTheEnds() = runComposeUiTest {
        var value by mutableStateOf(0.5f)
        setContent { MaterialTheme(s) { KSlider(value, { value = it }, Modifier.testTag("sl")) } }
        onNodeWithTag("sl").requestFocus()
        onNodeWithTag("sl").performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(0.51f, value, 0.0005f)
        onNodeWithTag("sl").performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionLeft) }
        assertEquals(0.49f, value, 0.0005f)
        onNodeWithTag("sl").performKeyInput { pressKey(Key.MoveEnd) }
        assertEquals(1f, value)
        onNodeWithTag("sl").performKeyInput { pressKey(Key.MoveHome) }
        assertEquals(0f, value)
    }

    @Test
    fun activeTrackAndThumbAreDrawnUpToTheValue() = runComposeUiTest {
        setContent { MaterialTheme(s) { Box(Modifier.width(220.dp)) { KSlider(0.5f, {}, Modifier.testTag("sl")) } } }
        val img = onNodeWithTag("sl").captureToImage().toPixelMap()
        assertEquals(220, img.width)
        assertTrue(img[40, 24].near(s.primary), "active track left of the thumb")
        assertTrue(img[180, 24].near(s.surfaceContainerHighest), "inactive track right of the thumb")
        assertTrue(img[110, 24].near(s.primary), "thumb centre at 50% is exactly in the middle")
    }

    @Test
    fun valueIsMirroredInRightToLeftLayouts() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(220.dp)) { KSlider(0.25f, {}, Modifier.testTag("sl")) }
                }
            }
        }
        val img = onNodeWithTag("sl").captureToImage().toPixelMap()
        assertTrue(img[200, 24].near(s.primary), "active track grows from the right edge")
        assertTrue(img[30, 24].near(s.surfaceContainerHighest), "inactive part is on the left")
    }

    @Test
    fun disabledSliderIgnoresInputAndOutOfRangeValuesAreCoerced() = runComposeUiTest {
        var value by mutableStateOf(5f)
        setContent {
            MaterialTheme(s) {
                androidx.compose.foundation.layout.Column(Modifier.width(220.dp)) {
                    KSlider(value, { value = it }, Modifier.testTag("off"), enabled = false)
                    KSlider(5f, {}, Modifier.testTag("over"))
                }
            }
        }
        onNodeWithTag("off").performTouchInput { click(Offset(100f, 24f)) }
        assertEquals(5f, value)
        assertEquals(1f, onNodeWithTag("over").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.current)
    }

    @Test
    fun hoverShowsAHaloAroundTheThumb() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent { MaterialTheme(s) { Box(Modifier.width(220.dp)) { KSlider(0.5f, {}, Modifier.testTag("sl"), interactionSource = source) } } }
        val before = onNodeWithTag("sl").captureToImage().toPixelMap()[110, 6].alpha
        runOnIdle { source.tryEmit(HoverInteraction.Enter()) }
        waitForIdle()
        val after = onNodeWithTag("sl").captureToImage().toPixelMap()[110, 6].alpha
        assertTrue(after > before + 0.04f, "halo missing: $before -> $after")
    }
}
