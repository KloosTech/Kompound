package tech.kloos.kompound.selection

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Geometry assumes density 1 (1dp = 1px), which the Compose UI test environment uses. */
@OptIn(ExperimentalTestApi::class)
class KSelectionControlsTest {
    private val s = ButtonTestScheme

    // ---- KCheckbox ----

    @Test
    fun checkboxToggleRequestsOppositeValueAndExposesState() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var requested: Boolean? = null
        setContent { MaterialTheme(s) { KCheckbox(checked, { requested = it; checked = it }, Modifier.testTag("c")) } }
        val config = { onNodeWithTag("c").fetchSemanticsNode().config }
        assertEquals(ToggleableState.Off, config().getOrNull(SemanticsProperties.ToggleableState))
        assertEquals(Role.Checkbox, config().getOrNull(SemanticsProperties.Role))
        onNodeWithTag("c").performClick()
        assertEquals(true, requested)
        assertEquals(ToggleableState.On, config().getOrNull(SemanticsProperties.ToggleableState))
    }

    @Test
    fun checkboxIs48dpAndDrawsFilledBoxWithCheckMarkWhenChecked() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KCheckbox(true, {}, Modifier.testTag("on"))
                    KCheckbox(false, {}, Modifier.testTag("off"))
                }
            }
        }
        assertEquals(48, onNodeWithTag("on").fetchSemanticsNode().size.width)
        assertEquals(48, onNodeWithTag("on").fetchSemanticsNode().size.height)
        val on = onNodeWithTag("on").captureToImage()
        val off = onNodeWithTag("off").captureToImage()
        assertTrue(on.toPixelMap()[24, 17].near(s.primary), "checked fill")
        assertTrue(on.containsColor(s.onPrimary), "check mark")
        assertTrue(off.toPixelMap()[24, 15].near(s.onSurfaceVariant), "unchecked outline")
        assertEquals(0f, off.toPixelMap()[24, 24].alpha, 0.01f)
        assertTrue(!off.containsColor(s.onPrimary), "no check mark when off")
    }

    @Test
    fun indeterminateShowsFilledBoxWithDashAndAnnouncesIt() = runComposeUiTest {
        setContent { MaterialTheme(s) { KCheckbox(ToggleableState.Indeterminate, {}, Modifier.testTag("c")) } }
        val img = onNodeWithTag("c").captureToImage()
        assertTrue(img.toPixelMap()[24, 17].near(s.primary))
        assertTrue(img.containsColor(s.onPrimary))
        assertEquals(ToggleableState.Indeterminate, onNodeWithTag("c").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState))
    }

    @Test
    fun clickingTheLabelTogglesTheCheckbox() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent { MaterialTheme(s) { KCheckbox(checked, { checked = it }, Modifier.testTag("c"), label = { KText("A long label") }) } }
        val size = onNodeWithTag("c").fetchSemanticsNode().size
        assertTrue(size.width > 48)
        onNodeWithTag("c").performTouchInput { click(Offset(size.width - 4f, size.height / 2f)) }
        assertTrue(checked)
    }

    @Test
    fun hoverShowsAHaloAroundTheBox() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent { MaterialTheme(s) { KCheckbox(false, {}, Modifier.testTag("c"), interactionSource = source) } }
        assertEquals(0f, onNodeWithTag("c").captureToImage().toPixelMap()[24, 6].alpha, 0.01f)
        runOnIdle { source.tryEmit(HoverInteraction.Enter()) }
        waitForIdle()
        mainClock.advanceTimeBy(500)
        assertTrue(onNodeWithTag("c").captureToImage().toPixelMap()[24, 6].alpha > 0.04f, "halo missing")
    }

    @Test
    fun checkboxDisabledIgnoresInputAndNullCallbackIsDisplayOnly() = runComposeUiTest {
        var calls = 0
        setContent {
            MaterialTheme(s) {
                Column {
                    KCheckbox(false, { calls++ }, Modifier.testTag("disabled"), enabled = false)
                    KCheckbox(false, null, Modifier.testTag("static"))
                }
            }
        }
        onNodeWithTag("disabled").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
        onNodeWithTag("static").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
    }

    // ---- KRadioButton ----

    @Test
    fun radioExposesSelectionAndRoleAndReportsClicks() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(s) {
                Column {
                    KRadioButton(true, { clicks++ }, Modifier.testTag("on"))
                    KRadioButton(false, { clicks += 10 }, Modifier.testTag("off"))
                }
            }
        }
        val on = onNodeWithTag("on").fetchSemanticsNode().config
        assertEquals(true, on.getOrNull(SemanticsProperties.Selected))
        assertEquals(Role.RadioButton, on.getOrNull(SemanticsProperties.Role))
        onNodeWithTag("off").performClick()
        assertEquals(10, clicks)
    }

    @Test
    fun radioSelectedHasPrimaryRingAndDotUnselectedHasNeither() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KRadioButton(true, {}, Modifier.testTag("on"))
                    KRadioButton(false, {}, Modifier.testTag("off"))
                }
            }
        }
        val on = onNodeWithTag("on").captureToImage().toPixelMap()
        val off = onNodeWithTag("off").captureToImage().toPixelMap()
        assertTrue(on[24, 15].near(s.primary), "selected ring")
        assertTrue(on[24, 24].near(s.primary), "selected dot")
        assertTrue(off[24, 15].near(s.onSurfaceVariant), "unselected ring")
        assertEquals(0f, off[24, 24].alpha, 0.01f)
    }

    // ---- KSwitch ----

    @Test
    fun switchTogglesAndExposesSwitchRole() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent { MaterialTheme(s) { KSwitch(checked, { checked = it }, Modifier.testTag("sw")) } }
        val config = { onNodeWithTag("sw").fetchSemanticsNode().config }
        assertEquals(Role.Switch, config().getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.Off, config().getOrNull(SemanticsProperties.ToggleableState))
        onNodeWithTag("sw").performClick()
        assertTrue(checked)
        assertEquals(ToggleableState.On, config().getOrNull(SemanticsProperties.ToggleableState))
    }

    @Test
    fun switchTrackAndThumbPositionsMatchTheStateExactly() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KSwitch(true, {}, Modifier.testTag("on"))
                    KSwitch(false, {}, Modifier.testTag("off"))
                }
            }
        }
        val size = onNodeWithTag("on").fetchSemanticsNode().size
        assertEquals(52, size.width)
        assertEquals(48, size.height)
        val on = onNodeWithTag("on").captureToImage().toPixelMap()
        val off = onNodeWithTag("off").captureToImage().toPixelMap()
        assertTrue(on[10, 24].near(s.primary), "on track")
        assertTrue(on[36, 24].near(s.onPrimary), "on thumb centre (36dp)")
        assertTrue(off[40, 24].near(s.surfaceContainerHighest), "off track")
        assertTrue(off[16, 24].near(s.outline), "off thumb centre (16dp)")
    }

    @Test
    fun switchAnimatesBetweenStatesInsteadOfJumping() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent { MaterialTheme(s) { KSwitch(checked, { checked = it }, Modifier.testTag("sw")) } }
        mainClock.autoAdvance = false
        checked = true
        mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
        val early = onNodeWithTag("sw").captureToImage().toPixelMap()
        assertTrue(!early[36, 24].near(s.onPrimary, 0.02f), "thumb should still be travelling after two frames")
        mainClock.advanceTimeBy(1000)
        val done = onNodeWithTag("sw").captureToImage().toPixelMap()
        assertTrue(done[36, 24].near(s.onPrimary), "thumb should arrive at 36dp")
        assertTrue(done[10, 24].near(s.primary), "track should end up primary")
    }

    @Test
    fun switchLabelTogglesAndDisabledIgnoresInput() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var calls = 0
        setContent {
            MaterialTheme(s) {
                Column {
                    KSwitch(checked, { checked = it }, Modifier.testTag("a"), label = { KText("Wi-Fi") })
                    KSwitch(false, { calls++ }, Modifier.testTag("b"), enabled = false)
                }
            }
        }
        val size = onNodeWithTag("a").fetchSemanticsNode().size
        onNodeWithTag("a").performTouchInput { click(Offset(size.width - 4f, size.height / 2f)) }
        assertTrue(checked)
        onNodeWithTag("b").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
    }
}
