package tech.kloos.kompound.accordion

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KAccordionStateTest {
    @Test
    fun togglesAndReportsOpenKeys() {
        val state = KAccordionState()
        state.toggle("a"); state.toggle("b")
        assertEquals(setOf("a", "b"), state.expandedKeys)
        state.toggle("a")
        assertFalse(state.isExpanded("a"))
        state.collapseAll()
        assertEquals(emptySet(), state.expandedKeys)
    }

    @Test
    fun exclusiveOpensOneAtATime() {
        val state = KAccordionState(exclusive = true)
        state.setExpanded("a", true); state.setExpanded("b", true)
        assertEquals(setOf("b"), state.expandedKeys)
        state.setExpanded("b", false)
        assertEquals(emptySet(), state.expandedKeys)
    }

    @Test
    fun saverRoundTripsTheOpenSections() {
        val saver = KAccordionState.saver(exclusive = false)
        val saved = with(saver) { SaverScope { true }.save(KAccordionState(false, setOf("x", "y"))) }!!
        val restored = saver.restore(saved)!!
        assertEquals(setOf("x", "y"), restored.expandedKeys)
    }
}

@OptIn(ExperimentalTestApi::class)
class KExpandableTest {
    private val s = ButtonTestScheme

    @Test
    fun headerTogglesTheContent() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent { MaterialTheme(s) { KExpandable("Shipping", expanded, { expanded = it }) { KText("Details") } } }
        onNodeWithText("Details").assertDoesNotExist()
        onNodeWithText("Shipping").performClick()
        waitForIdle()
        onNodeWithText("Details").assertExists()
        onNodeWithText("Shipping").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1000)
        onNodeWithText("Details").assertDoesNotExist()
    }

    @Test
    fun headerExposesExpandAndCollapseActions() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        setContent { MaterialTheme(s) { KExpandable("T", expanded, { expanded = it }, Modifier.testTag("e")) { KText("c") } } }
        fun config() = onNodeWithText("T").fetchSemanticsNode().config
        assertTrue(config().contains(SemanticsActions.Expand), "expand action missing")
        expanded = true
        waitForIdle()
        assertTrue(config().contains(SemanticsActions.Collapse), "collapse action missing")
    }

    @Test
    fun disabledHeaderIgnoresClicks() = runComposeUiTest {
        var changes = 0
        setContent { MaterialTheme(s) { KExpandable("T", false, { changes++ }, enabled = false) { KText("c") } } }
        onNodeWithText("T").assertIsNotEnabled()
        runCatching { onNodeWithText("T").performClick() }
        assertEquals(0, changes)
    }

    @Test
    fun customHeaderSlotIsShown() = runComposeUiTest {
        setContent { MaterialTheme(s) { KExpandable(true, {}, header = { KText("Custom header") }) { KText("Body") } } }
        onNodeWithText("Custom header").assertExists()
        onNodeWithText("Body").assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
class KAccordionComposableTest {
    private val s = ButtonTestScheme

    @Test
    fun exclusiveAccordionClosesTheOtherSection() = runComposeUiTest {
        val state = KAccordionState(exclusive = true)
        setContent {
            MaterialTheme(s) {
                KAccordion(state = state) {
                    Item("a", "First") { KText("A body") }
                    Item("b", "Second") { KText("B body") }
                }
            }
        }
        onNodeWithText("First").performClick()
        waitForIdle()
        onNodeWithText("A body").assertExists()
        onNodeWithText("Second").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1000)
        onNodeWithText("B body").assertExists()
        onNodeWithText("A body").assertDoesNotExist()
    }

    @Test
    fun initiallyExpandedSectionsAreOpen() = runComposeUiTest {
        setContent { MaterialTheme(s) { KAccordion(state = KAccordionState(false, setOf("a"))) { Item("a", "First") { KText("Open body") } } } }
        onNodeWithText("Open body").assertExists()
    }
}
