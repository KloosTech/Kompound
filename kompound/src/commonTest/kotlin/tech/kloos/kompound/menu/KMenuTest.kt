package tech.kloos.kompound.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.containsColor
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KMenuTest {
    private val s = ButtonTestScheme

    @Test
    fun itemsAreShownOnlyWhileExpanded() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent { MaterialTheme(s) { KMenu(expanded, { expanded = false }) { KMenuItem("Edit", {}) } } }
        onNodeWithText("Edit").assertDoesNotExist()
        expanded = true
        waitForIdle()
        onNodeWithText("Edit").assertExists()
    }

    @Test
    fun clickingAnActionItemCallsItAndExposesButtonRole() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(s) { KMenu(true, {}) { KMenuItem("Delete", { clicks++ }) } } }
        val item = onNodeWithText("Delete")
        assertEquals(Role.Button, item.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        item.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun selectedActionItemExposesSelectedStateAndPlainOnesDoNot() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KMenu(true, {}) {
                    KMenuItem("Marked", {}, selected = true, showCheck = true)
                    KMenuItem("Plain", {})
                }
            }
        }
        assertEquals(true, onNodeWithText("Marked").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(null, onNodeWithText("Plain").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun selectableItemsExposeSelectedStateAndDrawACheck() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KMenu(true, {}) {
                    KMenuItem("Chosen", {}, selected = true, showCheck = true, role = Role.RadioButton)
                    KMenuItem("Other", {}, selected = false, showCheck = true, role = Role.RadioButton)
                }
            }
        }
        val chosen = onNodeWithText("Chosen")
        assertEquals(true, chosen.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, onNodeWithText("Other").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(Role.RadioButton, chosen.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertTrue(chosen.captureToImage().containsColor(s.secondaryContainer), "selected item is tinted")
        assertTrue(chosen.captureToImage().containsColor(s.onSecondaryContainer), "check mark and text use onSecondaryContainer")
    }

    @Test
    fun itemsFillTheMenuWidthSoTheSelectedTintSpansIt() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KMenu(true, {}, minWidth = 300.dp) {
                    KMenuItem("Short", {}, Modifier.testTag("short"), selected = true, showCheck = true, role = Role.RadioButton)
                    KMenuItem("A rather longer label", {}, Modifier.testTag("long"), role = Role.RadioButton)
                }
            }
        }
        val short = onNodeWithTag("short").fetchSemanticsNode().size.width
        val long = onNodeWithTag("long").fetchSemanticsNode().size.width
        assertEquals(long, short, "items have different widths")
        assertTrue(short >= 300 - 2, "items narrower than the menu")
    }

    @Test
    fun checkMarkSitsAtTheEndOfTheRow() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KMenu(true, {}, minWidth = 300.dp) {
                    KMenuItem("Short", {}, Modifier.testTag("item"), selected = true, showCheck = true, role = Role.RadioButton)
                }
            }
        }
        val image = onNodeWithTag("item").captureToImage().toPixelMap()
        val ink = s.onSecondaryContainer
        fun isInk(x: Int, y: Int) = image[x, y].let { abs(it.red - ink.red) < 0.05f && abs(it.green - ink.green) < 0.05f && abs(it.blue - ink.blue) < 0.05f }
        val lastInkColumn = (image.width - 1 downTo 0).first { x -> (0 until image.height).any { y -> isInk(x, y) } }
        assertTrue(lastInkColumn > image.width - 40, "check mark ends at x=$lastInkColumn of ${image.width}")
    }

    @Test
    fun disabledItemIgnoresClicks() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(s) { KMenu(true, {}) { KMenuItem("Locked", { clicks++ }, enabled = false) } } }
        onNodeWithText("Locked").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun popupGoesBelowTheAnchorWhenItFits() {
        val pos = calculateDropdownPosition(IntRect(10, 20, 110, 60), IntSize(400, 600), IntSize(120, 200))
        assertEquals(IntOffset(10, 60), pos)
    }

    @Test
    fun popupFlipsAboveWhenThereIsNoRoomBelow() {
        val pos = calculateDropdownPosition(IntRect(10, 500, 110, 540), IntSize(400, 600), IntSize(120, 200))
        assertEquals(IntOffset(10, 300), pos)
    }

    @Test
    fun popupIsClampedInsideTheWindow() {
        // too far right: slides left; too tall for either side: pinned to the bottom edge
        val right = calculateDropdownPosition(IntRect(380, 20, 400, 60), IntSize(400, 600), IntSize(120, 100))
        assertEquals(280, right.x)
        val tall = calculateDropdownPosition(IntRect(0, 300, 50, 340), IntSize(400, 600), IntSize(100, 500))
        assertEquals(100, tall.y)
        val huge = calculateDropdownPosition(IntRect(0, 300, 50, 340), IntSize(400, 600), IntSize(100, 800))
        assertEquals(0, huge.y)
    }
}
