package tech.kloos.kompound.slide

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSlideToConfirmTest {
    private val scheme = lightColorScheme()

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        onConfirm: () -> Unit,
        enabled: Boolean = true,
        rtl: Boolean = false,
        resetAfterMillis: Long? = 1_500,
        confirmedLabel: String? = "Deleting",
    ) = setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            MaterialTheme(scheme) {
                KSlideToConfirm("Slide to delete", onConfirm, Modifier.testTag("s").width(300.dp), confirmedLabel = confirmedLabel, enabled = enabled,
                    animated = false, confirmDelayMillis = 200, resetAfterMillis = resetAfterMillis)
            }
        }
    }

    @Test
    fun draggingAllTheWayConfirmsAfterTheDelayAndShowsTheConfirmedLabel() = runComposeUiTest {
        var confirms = 0
        show({ confirms++ })
        mainClock.autoAdvance = false
        onNodeWithTag("s").performTouchInput { swipe(Offset(30f, height / 2f), Offset(width - 10f, height / 2f), durationMillis = 300) }
        mainClock.advanceTimeBy(100)
        assertEquals(0, confirms, "confirmed before the delay")
        mainClock.advanceTimeBy(800)
        assertEquals(1, confirms)
        mainClock.autoAdvance = true
        onNodeWithText("Deleting", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aShortDragSpringsBackWithoutConfirming() = runComposeUiTest {
        var confirms = 0
        show({ confirms++ })
        onNodeWithTag("s").performTouchInput { swipe(Offset(30f, height / 2f), Offset(110f, height / 2f), durationMillis = 200) }
        mainClock.advanceTimeBy(3_000)
        assertEquals(0, confirms)
        onNodeWithText("Slide to delete", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theControlReturnsToItsStartAfterConfirming() = runComposeUiTest {
        var confirms = 0
        show({ confirms++ }, resetAfterMillis = 500)
        onNodeWithTag("s").performTouchInput { swipeRight(startX = 30f, endX = width - 10f, durationMillis = 300) }
        mainClock.advanceTimeBy(4_000)
        assertEquals(1, confirms)
        onNodeWithText("Slide to delete", useUnmergedTree = true).assertExists()
        // and it can be confirmed again
        onNodeWithTag("s").performTouchInput { swipeRight(startX = 30f, endX = width - 10f, durationMillis = 300) }
        mainClock.advanceTimeBy(4_000)
        assertEquals(2, confirms)
    }

    @Test
    fun accessibilityClickAndEnterConfirmWithoutDragging() = runComposeUiTest {
        var confirms = 0
        show({ confirms++ })
        assertTrue(onNodeWithTag("s").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        onNodeWithTag("s").performSemanticsAction(SemanticsActions.OnClick)
        mainClock.advanceTimeBy(3_000)
        assertEquals(1, confirms)
        onNodeWithTag("s").requestFocus()
        onNodeWithTag("s").performKeyInput { pressKey(Key.Enter) }
        mainClock.advanceTimeBy(3_000)
        assertEquals(2, confirms)
    }

    @Test
    fun isAnnouncedAsAButtonWithItsLabel() = runComposeUiTest {
        show({})
        val config = onNodeWithTag("s").fetchSemanticsNode().config
        assertEquals("Slide to delete", config.getOrNull(SemanticsProperties.ContentDescription)?.single())
        assertEquals(androidx.compose.ui.semantics.Role.Button, config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun disabledIgnoresDragsAndClicks() = runComposeUiTest {
        var confirms = 0
        show({ confirms++ }, enabled = false)
        onNodeWithTag("s").assertIsNotEnabled()
        onNodeWithTag("s").performTouchInput { swipeRight(startX = 30f, endX = width - 10f, durationMillis = 300) }
        mainClock.advanceTimeBy(3_000)
        assertEquals(0, confirms)
    }

    @Test
    fun inRightToLeftTheHandleStartsOnTheRightAndConfirmsByDraggingLeft() = runComposeUiTest {
        var confirms = 0
        show({ confirms++ }, rtl = true)
        // dragging right (the wrong way) does nothing
        onNodeWithTag("s").performTouchInput { swipe(Offset(width - 30f, height / 2f), Offset(width - 5f, height / 2f), durationMillis = 200) }
        mainClock.advanceTimeBy(3_000)
        assertEquals(0, confirms)
        onNodeWithTag("s").performTouchInput { swipe(Offset(width - 30f, height / 2f), Offset(10f, height / 2f), durationMillis = 300) }
        mainClock.advanceTimeBy(3_000)
        assertEquals(1, confirms)
    }

    @Test
    fun aNewDragDuringTheSpringBackNeverLeavesTheHandleStuckHalfway() = runComposeUiTest {
        var confirms = 0
        show({ confirms++ })
        mainClock.autoAdvance = false
        repeat(6) { i ->
            onNodeWithTag("s").performTouchInput { swipe(Offset(30f, height / 2f), Offset(110f + i * 15f, height / 2f), durationMillis = 80) }
            mainClock.advanceTimeBy(40L + i * 25L)     // the next drag starts while the previous one is still springing back
        }
        mainClock.advanceTimeBy(5_000)
        assertEquals(0, confirms)
        val image = onNodeWithTag("s").captureToImage().toPixelMap()
        // handle at the start (primary), nothing filled to the right of it
        assertTrue(image[30, image.height / 2].let { abs(it.red - scheme.primary.red) < 0.1f && abs(it.blue - scheme.primary.blue) < 0.1f }, "handle is not back at the start")
        val atRest = image[120, 8]   // above the label text, right of where the handle rests
        val track = scheme.primary.copy(alpha = 0.12f).compositeOver(scheme.surface)
        assertTrue(abs(atRest.red - track.red) < 0.06f && abs(atRest.blue - track.blue) < 0.06f, "fill left behind: $atRest")
    }
}
