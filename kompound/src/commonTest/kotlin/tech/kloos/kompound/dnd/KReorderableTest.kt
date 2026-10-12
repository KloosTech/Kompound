package tech.kloos.kompound.dnd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KReorderableTest {
    private val scheme = lightColorScheme()
    private var items by mutableStateOf(listOf("A", "B", "C"))
    private val reorders = mutableListOf<List<String>>()

    private fun ComposeUiTest.show(enabled: Boolean = true, showHandle: Boolean = true) = setContent {
        MaterialTheme(scheme) {
            Box(Modifier.size(300.dp, 400.dp)) {
                KReorderableColumn(
                    items = items,
                    onReorder = { reorders += it; items = it },
                    key = { it },
                    enabled = enabled,
                    showHandle = showHandle,
                ) { item -> KText(item, Modifier.fillMaxWidth().height(40.dp).testTag("row-$item")) }
            }
        }
    }

    private fun ComposeUiTest.row(item: String) = onNodeWithTag("row-$item")

    /** Focuses the focusable container of a row and presses Alt+[key]. */
    private fun ComposeUiTest.altKey(item: String, key: Key) {
        var n: androidx.compose.ui.semantics.SemanticsNode? = onNodeWithTag("row-$item").fetchSemanticsNode()
        while (n != null && n.config.getOrNull(SemanticsActions.RequestFocus) == null) n = n.parent
        val node = checkNotNull(n) { "no focusable container above row-$item" }
        runOnUiThread { node.config[SemanticsActions.RequestFocus].action!!() }
        waitForIdle()
        onNodeWithTag("row-$item").performKeyInput { keyDown(Key.AltLeft); pressKey(key); keyUp(Key.AltLeft) }
        waitForIdle()
    }

    @Test
    fun rendersAllRowsWithAHandleEach() = runComposeUiTest {
        show()
        waitForIdle()
        items.forEach { row(it).assertExists() }
        assertEquals(3, onAllNodesWithContentDescription("Drag handle").fetchSemanticsNodes().size)
    }

    @Test
    fun withoutHandlesNoHandleIsShown() = runComposeUiTest {
        show(showHandle = false)
        waitForIdle()
        assertEquals(0, onAllNodesWithContentDescription("Drag handle").fetchSemanticsNodes().size)
    }

    @Test
    fun altDownMovesTheFocusedRowOneStepDown() = runComposeUiTest {
        show()
        waitForIdle()
        altKey("A", Key.DirectionDown)
        assertEquals(listOf("B", "A", "C"), items)
        assertEquals(1, reorders.size)
    }

    @Test
    fun theFirstRowOffersNoMoveUpAndTheLastNoMoveDown() = runComposeUiTest {
        show()
        waitForIdle()
        // the actions live on the item container above the text; find it by walking up
        fun labelsAbove(item: String): List<String> {
            var n: androidx.compose.ui.semantics.SemanticsNode? = onNodeWithTag("row-$item").fetchSemanticsNode()
            while (n != null) {
                n.config.getOrNull(SemanticsActions.CustomActions)?.let { return it.map { a -> a.label } }
                n = n.parent
            }
            return emptyList()
        }
        assertEquals(listOf("Move down"), labelsAbove("A"))
        assertEquals(listOf("Move up", "Move down"), labelsAbove("B"))
        assertEquals(listOf("Move up"), labelsAbove("C"))
    }

    @Test
    fun aCustomActionMovesTheRow() = runComposeUiTest {
        show()
        waitForIdle()
        var n: androidx.compose.ui.semantics.SemanticsNode? = onNodeWithTag("row-C").fetchSemanticsNode()
        var up: (() -> Boolean)? = null
        while (n != null && up == null) {
            up = n.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == "Move up" }?.let { a -> { a.action() } }
            n = n.parent
        }
        runOnUiThread { up!!() }
        waitForIdle()
        assertEquals(listOf("A", "C", "B"), items)
    }

    @Test
    fun aDisabledListNeverReorders() = runComposeUiTest {
        show(enabled = false)
        waitForIdle()
        var n: androidx.compose.ui.semantics.SemanticsNode? = onNodeWithTag("row-A").fetchSemanticsNode()
        while (n != null) {
            assertEquals(null, n.config.getOrNull(SemanticsActions.CustomActions), "a disabled list offers move actions")
            assertEquals(null, n.config.getOrNull(SemanticsActions.RequestFocus), "a disabled list is focusable")
            n = n.parent
        }
        assertTrue(reorders.isEmpty())
        assertEquals(listOf("A", "B", "C"), items)
    }

    @Test
    fun draggingAHandlePastTheNextRowReportsTheNewOrderOnceOnDrop() = runComposeUiTest {
        show()
        waitForIdle()
        val handle = onAllNodesWithContentDescription("Drag handle")[0]
        mainClock.autoAdvance = false
        val bounds = handle.fetchSemanticsNode().boundsInRoot
        val start = bounds.center
        handle.performTouchInput { down(center) }
        mainClock.advanceTimeBy(100)
        repeat(30) {
            handle.performTouchInput { moveBy(Offset(0f, 4f)) }
            mainClock.advanceTimeBy(16)
        }
        repeat(30) { mainClock.advanceTimeBy(16) }
        assertTrue(reorders.isEmpty(), "reported before the drop")
        handle.performTouchInput { up() }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(1, reorders.size, "reorders=$reorders start=$start")
        assertEquals("A", reorders.single().let { it.first { x -> x == "A" } })
        assertTrue(reorders.single().indexOf("A") > 0)
    }
}
