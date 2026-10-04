package tech.kloos.kompound.tree

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KTreeViewTest {
    private class N(val id: String, val kids: List<N> = emptyList())

    private val tree = listOf(N("a", listOf(N("a1"), N("a2", listOf(N("a2x"))))), N("b"))

    @Test
    fun stateTogglesAndRevealsAndSaves() {
        val s = KTreeState()
        s.toggle("a"); assertEquals(setOf("a"), s.expanded)
        s.toggle("a"); assertEquals(emptySet(), s.expanded)
        s.reveal("a2x", listOf("a", "a2"))
        assertEquals(setOf("a", "a2"), s.expanded)
        assertEquals("a2x", s.selected)
        val saved = with(KTreeState.Saver) { SaverScope { true }.save(s) }!!
        val back = KTreeState.Saver.restore(saved)!!
        assertEquals(s.expanded, back.expanded)
        assertEquals("a2x", back.selected)
    }

    @Test
    fun closedNodesHideTheirChildren() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                KTreeView(tree, { it.kids }, { it.id }, Modifier.height(300.dp), state = KTreeState(setOf("a"))) { n, _ -> KText(n.id) }
            }
        }
        onNodeWithText("a1").assertIsDisplayed()
        onAllNodesWithText("a2x").assertCountEquals(0)
    }

    @Test
    fun clickSelectsAndCallsBack() = runComposeUiTest {
        var picked: String? = null
        val state = KTreeState()
        setContent {
            MaterialTheme(lightColorScheme()) {
                KTreeView(tree, { it.kids }, { it.id }, Modifier.height(300.dp), state = state, onSelect = { picked = it.id }) { n, _ -> KText(n.id) }
            }
        }
        onNodeWithText("b").performClick()
        assertEquals("b", picked)
        assertEquals("b", state.selected)
    }
}
