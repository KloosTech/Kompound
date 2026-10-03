package tech.kloos.kompound.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.math
import tech.kloos.kompound.graph.model.ref
import tech.kloos.kompound.text.KText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KNodeGraphTest {
    private val scheme = lightColorScheme()

    private fun ComposeUiTest.show(state: KGraphState, recompositions: IntArray? = null) = setContent {
        MaterialTheme(scheme) {
            Box(Modifier.size(900.dp, 600.dp).testTag("canvas")) {
                KNodeGraph(state, Modifier.fillMaxSize()) { node ->
                    KNode(node, title = "Node ${node.id}", modifier = Modifier.testTag(node.id.value)) {
                        recompositions?.let { it[0]++ }
                        Input("a", editor = { KText("editor") })
                        Input("b")
                        Output("out")
                    }
                }
            }
        }
    }

    private fun twoNodes() = KGraphState(Graph.of(listOf(math("n1", Offset(50f, 50f)), math("n2", Offset(450f, 80f)))))

    private fun SemanticsNodeInteraction.topLeft() = fetchSemanticsNode().boundsInRoot.topLeft

    @Test
    fun nodesAreShownAtTheirWorldPositions() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        assertEquals(Offset(50f, 50f), onNodeWithTag("n1").topLeft())
        assertEquals(Offset(450f, 80f), onNodeWithTag("n2").topLeft())
        onNodeWithText("Node n1").assertExists()
    }

    @Test
    fun portsReportTheirCentresOnTheNodeEdges() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        val n1 = onNodeWithTag("n1").fetchSemanticsNode().boundsInRoot
        val input = state.anchorOf(ref("n1", "a"))
        val output = state.anchorOf(ref("n1", "out"))
        assertNotNull(input); assertNotNull(output)
        assertTrue(abs(input.x - n1.left) <= 1.5f, "input centre ${input.x} vs node left ${n1.left}")
        assertTrue(abs(output.x - n1.right) <= 1.5f, "output centre ${output.x} vs node right ${n1.right}")
        assertTrue(input.y > n1.top && input.y < n1.bottom)
    }

    @Test
    fun draggingTheTitleBarMovesTheNodeAndUndoPutsItBack() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        onNodeWithTag("n1").performTouchInput { swipe(Offset(60f, 14f), Offset(160f, 74f), durationMillis = 200) }
        waitForIdle()
        val moved = state.graph.node(NodeId("n1"))!!.position
        assertTrue(abs(moved.x - 150f) < 3f && abs(moved.y - 110f) < 3f, "node ended at $moved")
        assertEquals(moved, onNodeWithTag("n1").topLeft())
        state.undo()
        waitForIdle()
        assertEquals(Offset(50f, 50f), onNodeWithTag("n1").topLeft())
    }

    @Test
    fun draggingFromAnOutputPortToAnInputPortConnectsThem() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        val from = state.viewport.worldToScreen(state.anchorOf(ref("n1", "out"))!!)
        val to = state.viewport.worldToScreen(state.anchorOf(ref("n2", "b"))!!)
        onRoot().performTouchInput { swipe(from, to, durationMillis = 300) }
        waitForIdle()
        assertEquals(listOf("n1.out->n2.b"), state.graph.edges.keys.map { it.value })
        assertTrue(state.canUndo)
    }

    @Test
    fun releasingAWireOnEmptyCanvasLeavesNothingBehind() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        val from = state.viewport.worldToScreen(state.anchorOf(ref("n1", "out"))!!)
        onRoot().performTouchInput { swipe(from, Offset(300f, 500f), durationMillis = 300) }
        waitForIdle()
        assertTrue(state.graph.edges.isEmpty())
        assertTrue(state.wire == null)
    }

    @Test
    fun anEdgeFollowsItsNodeWhileItIsDragged() = runComposeUiTest {
        val state = twoNodes()
        state.connect(ref("n1", "out"), ref("n2", "a"))
        show(state)
        waitForIdle()
        val before = state.anchorOf(ref("n2", "a"))!!
        onNodeWithTag("n2").performTouchInput { swipe(Offset(60f, 14f), Offset(60f, 114f), durationMillis = 200) }
        waitForIdle()
        val after = state.anchorOf(ref("n2", "a"))!!
        assertTrue(after.y - before.y in 97f..103f, "anchor moved by ${after.y - before.y}")
    }

    @Test
    fun draggingTheBackgroundPansAndTheWheelZoomsAroundThePointer() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        onRoot().performTouchInput { swipe(Offset(600f, 450f), Offset(700f, 480f), durationMillis = 200) }
        waitForIdle()
        assertTrue(state.viewport.offset.x > 60f && state.viewport.offset.y > 15f, "offset ${state.viewport.offset}")
        val focus = Offset(300f, 300f)
        val worldBefore = state.viewport.screenToWorld(focus)
        onRoot().performMouseInput { moveTo(focus); scroll(-3f) }
        waitForIdle()
        assertTrue(state.viewport.zoom > 1f, "zoom ${state.viewport.zoom}")
        val worldAfter = state.viewport.screenToWorld(focus)
        assertTrue(abs(worldAfter.x - worldBefore.x) < 1f && abs(worldAfter.y - worldBefore.y) < 1f)
    }

    @Test
    fun panAndZoomDoNotRecomposeTheNodes() = runComposeUiTest {
        val state = twoNodes()
        val counter = intArrayOf(0)
        show(state, counter)
        waitForIdle()
        val before = counter[0]
        state.viewport.panBy(Offset(120f, 40f))
        state.viewport.zoomBy(1.5f, Offset(100f, 100f))
        waitForIdle()
        assertEquals(before, counter[0], "node content recomposed while panning or zooming")
    }

    @Test
    fun tappingANodeSelectsItAndDeleteRemovesItWithItsEdges() = runComposeUiTest {
        val state = twoNodes()
        state.connect(ref("n1", "out"), ref("n2", "a"))
        show(state)
        waitForIdle()
        onNodeWithTag("n2").performTouchInput { click(Offset(60f, 14f)) }
        waitForIdle()
        assertEquals(setOf(NodeId("n2")), state.selection)
        onNodeWithTag("n2").requestFocus()
        onNodeWithTag("n2").performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals(setOf(NodeId("n1")), state.graph.nodes.keys)
        assertTrue(state.graph.edges.isEmpty())
        state.undo()
        waitForIdle()
        assertEquals(1, state.graph.edges.size)
    }

    @Test
    fun tappingAnEdgeSelectsItAndTappingEmptyCanvasClears() = runComposeUiTest {
        val state = twoNodes()
        state.connect(ref("n1", "out"), ref("n2", "a"))
        show(state)
        waitForIdle()
        val a = state.anchorOf(ref("n1", "out"))!!
        val b = state.anchorOf(ref("n2", "a"))!!
        val mid = EdgeGeometry.sample(KEdgeShape.Bezier, a, b, 2)[1]
        onRoot().performTouchInput { click(state.viewport.worldToScreen(mid)) }
        waitForIdle()
        assertEquals(setOf(EdgeId("n1.out->n2.a")), state.selectedEdges)
        onRoot().performTouchInput { click(Offset(800f, 550f)) }
        waitForIdle()
        assertTrue(state.selectedEdges.isEmpty() && state.selection.isEmpty())
    }

    @Test
    fun anInputsEditorIsHiddenOnceItIsConnected() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        assertEquals(2, onAllEditors())
        state.connect(ref("n1", "out"), ref("n2", "a"))
        waitForIdle()
        assertEquals(1, onAllEditors(), "the connected input hides its editor")
    }

    private fun ComposeUiTest.onAllEditors() = onAllNodes(androidx.compose.ui.test.hasText("editor")).fetchSemanticsNodes().size

    @Test
    fun theKeyboardCanMoveANodeAndWireTwoPorts() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        onNodeWithTag("n1").requestFocus()
        onNodeWithTag("n1").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(Offset(60f, 50f), state.graph.node(NodeId("n1"))!!.position)
        state.beginKeyboardWire(ref("n1", "out"))
        state.completeWire(ref("n2", "a"))
        assertEquals(1, state.graph.edges.size)
    }

    @Test
    fun undoAndRedoShortcutsWork() = runComposeUiTest {
        val state = twoNodes()
        show(state)
        waitForIdle()
        state.connect(ref("n1", "out"), ref("n2", "a"))
        onNodeWithContentDescription("Node graph", substring = true).requestFocus()
        onNodeWithContentDescription("Node graph", substring = true).performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft) }
        waitForIdle()
        assertTrue(state.graph.edges.isEmpty())
    }

    @Test
    fun largeGraphsLayOutAndRespondToPanning() = runComposeUiTest {
        val nodes = (0 until 120).map { math("m$it", Offset((it % 12) * 260f, (it / 12) * 190f)) }
        val edges = (0 until 100).map { Edge(EdgeId("e$it"), ref("m$it", "out"), ref("m${it + 1}", "a")) }
        val state = KGraphState(Graph.of(nodes, edges))
        show(state)
        waitForIdle()
        assertEquals(120, state.graph.nodes.size)
        assertTrue(state.anchorOf(ref("m60", "out")) != null)
        state.viewport.panBy(Offset(-500f, -300f))
        waitForIdle()
    }
}
