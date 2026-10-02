package tech.kloos.kompound.chip

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.graphics.Color
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.topCentre
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KChipTest {
    private val s = ButtonTestScheme.copy(onSurfaceVariant = Color(0xFFFF0080))

    @Test
    fun assistChipIsAClickableButton() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(s) { KChip("Label", onClick = { clicks++ }, Modifier.testTag("c")) } }
        val node = onNodeWithTag("c")
        node.performClick()
        assertEquals(1, clicks)
        assertEquals(Role.Button, node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun filterChipExposesCheckedStateAndRole() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KChip("On", onClick = {}, Modifier.testTag("on"), selected = true)
                    KChip("Off", onClick = {}, Modifier.testTag("off"), selected = false)
                }
            }
        }
        val on = onNodeWithTag("on").fetchSemanticsNode().config
        val off = onNodeWithTag("off").fetchSemanticsNode().config
        assertEquals(ToggleableState.On, on.getOrNull(SemanticsProperties.ToggleableState))
        assertEquals(ToggleableState.Off, off.getOrNull(SemanticsProperties.ToggleableState))
        assertEquals(Role.Checkbox, on.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun selectedIsFilledAndUnselectedIsOutlined() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KChip("MMMM", onClick = {}, Modifier.testTag("on"), selected = true)
                    KChip("MMMM", onClick = {}, Modifier.testTag("off"), selected = false)
                }
            }
        }
        val on = onNodeWithTag("on").captureToImage()
        val off = onNodeWithTag("off").captureToImage()
        assertTrue(on.topCentre().near(s.secondaryContainer), "selected container")
        assertEquals(0f, off.topCentre().alpha, 0.01f)
        assertTrue(off.containsColor(s.outline), "unselected outline")
        assertTrue(on.containsColor(s.onSecondaryContainer), "selected label")
        assertTrue(off.containsColor(s.onSurfaceVariant), "unselected label")
    }

    @Test
    fun leadingAndTrailingSlotsGetTheContentColour() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KChip("", onClick = {}, Modifier.testTag("off"), leading = { KIcon(SquareIcon, null) })
                    KChip("", onClick = {}, Modifier.testTag("on"), selected = true, trailing = { KIcon(SquareIcon, null) })
                }
            }
        }
        assertTrue(onNodeWithTag("off").captureToImage().containsColor(s.onSurfaceVariant), "leading, unselected")
        assertTrue(onNodeWithTag("on").captureToImage().containsColor(s.onSecondaryContainer), "trailing, selected")
    }

    @Test
    fun iconsNeverChangeTheChipHeight() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KChip("Label", onClick = {}, Modifier.testTag("plain"))
                    KChip("Label", onClick = {}, Modifier.testTag("lead"), leading = { KIcon(SquareIcon, null) })
                    KChip("Label", onClick = {}, Modifier.testTag("both"), selected = true,
                        leading = { KIcon(SquareIcon, null) }, trailing = { KIcon(SquareIcon, null) })
                }
            }
        }
        val plain = onNodeWithTag("plain").fetchSemanticsNode().size.height
        assertEquals(plain, onNodeWithTag("lead").fetchSemanticsNode().size.height, "leading icon")
        assertEquals(plain, onNodeWithTag("both").fetchSemanticsNode().size.height, "both icons")
    }

    @Test
    fun longLabelsStopAtTheMaximumWidth() = runComposeUiTest {
        setContent { MaterialTheme(s) { KChip("W".repeat(120), onClick = {}, Modifier.testTag("c")) } }
        assertTrue(onNodeWithTag("c").fetchSemanticsNode().size.width <= 250)
    }

    @Test
    fun disabledIgnoresClicks() = runComposeUiTest {
        var clicks = 0
        setContent { MaterialTheme(s) { KChip("x", onClick = { clicks++ }, Modifier.testTag("c"), enabled = false) } }
        onNodeWithTag("c").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }
}
