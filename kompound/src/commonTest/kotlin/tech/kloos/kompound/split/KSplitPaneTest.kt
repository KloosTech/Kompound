package tech.kloos.kompound.split

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSplitPaneTest {
    private val scheme = lightColorScheme()

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: KSplitPaneState, orientation: Orientation = Orientation.Horizontal, minFirst: androidx.compose.ui.unit.Dp = 80.dp, minSecond: androidx.compose.ui.unit.Dp = 80.dp) = setContent {
        MaterialTheme(scheme) {
            Box(Modifier.size(508.dp, 308.dp)) {
                KSplitPane(
                    first = { Box(Modifier.fillMaxSize().testTag("first")) }, second = { Box(Modifier.fillMaxSize().testTag("second")) },
                    state = state, orientation = orientation, minFirst = minFirst, minSecond = minSecond,
                )
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.size(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().size

    @Test
    fun theTwoPanesShareTheSpaceAccordingToTheFractionAroundTheDivider() = runComposeUiTest {
        val state = KSplitPaneState(0.25f)
        show(state)
        waitForIdle()
        val first = size("first").width
        val second = size("second").width
        assertTrue(kotlin.math.abs(first - (500 * 0.25f * density())) <= 2f * density() + 2, "first pane ~25% of the shared space: $first")
        assertTrue(second > first * 2, "second pane takes the rest: $first / $second")
    }

    private fun density() = 1f   // the desktop test window renders at density 1

    @Test
    fun draggingTheDividerChangesTheFractionAndPanesRespectTheirMinimums() = runComposeUiTest {
        val state = KSplitPaneState(0.5f)
        show(state, minFirst = 120.dp, minSecond = 100.dp)
        waitForIdle()
        onNodeWithContentDescription("Resize panes").performTouchInput { swipe(center, center + Offset(100f, 0f), durationMillis = 200) }
        waitForIdle()
        assertTrue(state.fraction > 0.6f, "dragged right: ${state.fraction}")
        onNodeWithContentDescription("Resize panes").performTouchInput { swipe(center, center + Offset(-2000f, 0f), durationMillis = 200) }
        waitForIdle()
        assertEquals(0f, state.fraction, "the fraction itself is clamped to 0..1")
        assertTrue(size("first").width >= 120, "but the pane keeps its minimum: ${size("first").width}")
        state.fraction = 1f
        waitForIdle()
        assertTrue(size("second").width >= 100, "second keeps its minimum: ${size("second").width}")
    }

    @Test
    fun arrowKeysHomeAndEndMoveTheDividerAndScreenReadersCanSetIt() = runComposeUiTest {
        val state = KSplitPaneState(0.5f)
        show(state)
        waitForIdle()
        val divider = onNodeWithContentDescription("Resize panes")
        divider.requestFocus()
        divider.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(0.54f, state.fraction, 0.001f)
        divider.performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(0.46f, state.fraction, 0.001f)
        divider.performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()
        assertEquals(1f, state.fraction)
        divider.performKeyInput { pressKey(Key.MoveHome) }
        waitForIdle()
        assertEquals(0f, state.fraction)
        val config = divider.fetchSemanticsNode().config
        assertEquals("0%", config.getOrNull(SemanticsProperties.StateDescription))
        divider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        waitForIdle()
        assertEquals(0.3f, state.fraction, 0.001f)
        assertEquals("30%", onNodeWithContentDescription("Resize panes").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun verticalPanesStackAndUpAndDownMoveTheDivider() = runComposeUiTest {
        val state = KSplitPaneState(0.5f)
        show(state, Orientation.Vertical)
        waitForIdle()
        assertTrue(size("first").height > 100 && size("second").height > 100)
        assertEquals(size("first").width, size("second").width)
        val divider = onNodeWithContentDescription("Resize panes")
        divider.requestFocus()
        divider.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertTrue(state.fraction > 0.5f)
    }

    @Test
    fun theFractionIsClampedAndSavedAndRestored() {
        assertEquals(1f, KSplitPaneState(7f).fraction)
        assertEquals(0f, KSplitPaneState(-1f).fraction)
        val saver = KSplitPaneState.Saver
        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(KSplitPaneState(0.37f)) }
        assertEquals(0.37f, saver.restore(saved!!)!!.fraction)
    }
}
