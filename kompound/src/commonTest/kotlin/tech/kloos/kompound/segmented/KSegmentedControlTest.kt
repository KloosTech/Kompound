package tech.kloos.kompound.segmented

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.topCentre
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.near
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSegmentedControlTest {
    private val s = ButtonTestScheme
    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun clickingASegmentReportsItsIndex() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        setContent { MaterialTheme(s) { KSegmentedControl(listOf("A", "B", "C"), selected, { selected = it }) } }
        onAllNodes(radio)[2].performClick()
        assertEquals(2, selected)
    }

    @Test
    fun segmentsAreRadioButtonsWithSelectedState() = runComposeUiTest {
        setContent { MaterialTheme(s) { KSegmentedControl(listOf("A", "B"), 1, {}) } }
        val nodes = onAllNodes(radio).fetchSemanticsNodes()
        assertEquals(2, nodes.size)
        assertEquals(false, nodes[0].config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, nodes[1].config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun selectedSegmentIsFilledAndTheOtherTransparent() = runComposeUiTest {
        setContent { MaterialTheme(s) { KSegmentedControl(listOf("MMMM", "MMMM"), 0, {}) } }
        val first = onAllNodes(radio)[0].captureToImage()
        val second = onAllNodes(radio)[1].captureToImage()
        assertTrue(first.topCentre().near(s.secondaryContainer), "selected container")
        assertEquals(0f, second.topCentre().alpha, 0.01f)
        assertTrue(first.containsColor(s.onSecondaryContainer), "selected label colour")
        assertTrue(second.containsColor(s.onSurface), "unselected label colour")
    }

    @Test
    fun segmentsShareTheWidthOfTheWidest() = runComposeUiTest {
        setContent { MaterialTheme(s) { KSegmentedControl(listOf("A", "A much longer label"), 0, {}) } }
        val w = onAllNodes(radio).fetchSemanticsNodes().map { it.size.width }
        assertEquals(w[0], w[1])
    }

    @Test
    fun disabledIgnoresClicks() = runComposeUiTest {
        var calls = 0
        setContent { MaterialTheme(s) { KSegmentedControl(listOf("A", "B"), 0, { calls++ }, enabled = false) } }
        onAllNodes(radio)[1].performClick()
        assertEquals(0, calls)
    }

    @Test
    fun customSegmentContentIsUsed() = runComposeUiTest {
        setContent {
            MaterialTheme(s) { KSegmentedControl(listOf("A"), 0, {}, segment = { i, _ -> tech.kloos.kompound.text.KText("custom$i", Modifier.testTag("c")) }) }
        }
        onNodeWithTag("c", useUnmergedTree = true).assertExists()
    }
}
