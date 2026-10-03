package tech.kloos.kompound.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import tech.kloos.kompound.graph.model.ConnectionCheck
import tech.kloos.kompound.graph.model.ConnectionRejection
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.math
import tech.kloos.kompound.graph.model.ref
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KViewportStateTest {
    private fun near(a: Offset, b: Offset) = abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f

    @Test
    fun screenAndWorldRoundTrip() {
        val v = KViewportState(Offset(30f, -20f), 2f)
        val p = Offset(123f, 456f)
        assertTrue(near(p, v.screenToWorld(v.worldToScreen(p))))
        assertEquals(Offset(30f, -20f), v.worldToScreen(Offset.Zero))
    }

    @Test
    fun zoomKeepsThePointUnderTheCursorFixed() {
        val v = KViewportState(Offset(40f, 10f), 1f)
        val focus = Offset(300f, 200f)
        val worldUnderCursor = v.screenToWorld(focus)
        v.zoomBy(1.5f, focus)
        assertEquals(1.5f, v.zoom, 0.0001f)
        assertTrue(near(worldUnderCursor, v.screenToWorld(focus)))
        v.zoomBy(0.5f, focus)
        assertTrue(near(worldUnderCursor, v.screenToWorld(focus)))
    }

    @Test
    fun zoomIsClamped() {
        val v = KViewportState(minZoom = 0.5f, maxZoom = 2f)
        v.zoomBy(100f, Offset.Zero)
        assertEquals(2f, v.zoom)
        v.zoomBy(0.0001f, Offset.Zero)
        assertEquals(0.5f, v.zoom)
    }

    @Test
    fun panMovesTheCanvas() {
        val v = KViewportState()
        v.panBy(Offset(10f, 5f))
        v.panBy(Offset(1f, 1f))
        assertEquals(Offset(11f, 6f), v.offset)
    }

    @Test
    fun fitCentresTheContentWithinTheCanvas() {
        val v = KViewportState()
        v.fit(Rect(0f, 0f, 1000f, 500f), Size(500f, 400f), padding = 0f)
        assertEquals(0.5f, v.zoom, 0.0001f)
        val centre = v.worldToScreen(Offset(500f, 250f))
        assertTrue(near(centre, Offset(250f, 200f)))
        v.fit(Rect(0f, 0f, 10f, 10f), Size(500f, 400f), padding = 0f)
        assertEquals(1f, v.zoom, "never zoomed in beyond 1:1")
    }
}

class EdgeGeometryTest {
    @Test
    fun distanceToABezierIsSmallOnTheCurveAndLargeAway() {
        val from = Offset(0f, 0f)
        val to = Offset(200f, 100f)
        val mid = EdgeGeometry.sample(KEdgeShape.Bezier, from, to, 2)[1]
        assertTrue(EdgeGeometry.distance(KEdgeShape.Bezier, from, to, mid) < 0.5f)
        assertTrue(EdgeGeometry.distance(KEdgeShape.Bezier, from, to, mid + Offset(0f, 80f)) > 30f)
        assertEquals(0f, EdgeGeometry.distance(KEdgeShape.Bezier, from, to, from), 0.001f)
    }

    @Test
    fun straightAndStepDistances() {
        assertEquals(10f, EdgeGeometry.distance(KEdgeShape.Straight, Offset(0f, 0f), Offset(100f, 0f), Offset(50f, 10f)), 0.001f)
        // step: horizontal to x=50, vertical to y=100, horizontal to 100
        assertEquals(5f, EdgeGeometry.distance(KEdgeShape.Step, Offset(0f, 0f), Offset(100f, 100f), Offset(55f, 50f)), 0.001f)
    }

    @Test
    fun degenerateEdgesDoNotBreak() {
        assertEquals(5f, EdgeGeometry.distance(KEdgeShape.Straight, Offset(10f, 10f), Offset(10f, 10f), Offset(13f, 14f)), 0.001f)
        assertTrue(EdgeGeometry.handle(Offset.Zero, Offset.Zero) >= 48f)
    }
}

class KGraphStateTest {
    private fun state(gridStep: Float = 0f) = KGraphState(
        Graph.of(listOf(math("n1", Offset(0f, 0f)), math("n2", Offset(300f, 0f)), math("n3", Offset(600f, 0f)))), gridStep = gridStep,
    ).also { s ->
        // pretend the ports reported their positions: output on the right, inputs on the left of every node
        for (n in s.graph.nodes.values) {
            s.anchors[PortRef(n.id, tech.kloos.kompound.graph.model.PortId("out"))] = n.position + Offset(200f, 40f)
            s.anchors[PortRef(n.id, tech.kloos.kompound.graph.model.PortId("a"))] = n.position + Offset(0f, 40f)
            s.anchors[PortRef(n.id, tech.kloos.kompound.graph.model.PortId("b"))] = n.position + Offset(0f, 80f)
        }
    }

    @Test
    fun selectionReplacesOrToggles() {
        val s = state()
        s.select(NodeId("n1"))
        s.select(NodeId("n2"), additive = true)
        assertEquals(setOf(NodeId("n1"), NodeId("n2")), s.selection)
        s.select(NodeId("n1"), additive = true)
        assertEquals(setOf(NodeId("n2")), s.selection)
        s.select(NodeId("n3"))
        assertEquals(setOf(NodeId("n3")), s.selection)
        s.selectAll()
        assertEquals(3, s.selection.size)
        s.clearSelection()
        assertTrue(s.selection.isEmpty())
        s.select(NodeId("ghost"))
        assertTrue(s.selection.isEmpty())
    }

    @Test
    fun draggingMovesTheSelectionAsOneUndoStep() {
        val s = state()
        s.select(NodeId("n1"))
        s.select(NodeId("n2"), additive = true)
        s.beginNodeDrag(NodeId("n1"))
        s.dragNodesBy(Offset(10f, 0f)); s.dragNodesBy(Offset(5f, 3f))
        assertEquals(Offset(15f, 3f), s.positionOf(s.graph.node(NodeId("n1"))!!))
        assertEquals(Offset(315f, 3f), s.positionOf(s.graph.node(NodeId("n2"))!!))
        assertEquals(Offset(600f, 0f), s.positionOf(s.graph.node(NodeId("n3"))!!), "unselected node does not move")
        assertEquals(Offset(0f, 0f), s.graph.node(NodeId("n1"))!!.position, "graph unchanged until release")
        s.endNodeDrag()
        assertEquals(Offset(15f, 3f), s.graph.node(NodeId("n1"))!!.position)
        assertEquals(Offset(315f, 3f), s.graph.node(NodeId("n2"))!!.position)
        s.undo()
        assertEquals(Offset(0f, 0f), s.graph.node(NodeId("n1"))!!.position)
        assertEquals(Offset(300f, 0f), s.graph.node(NodeId("n2"))!!.position)
        assertFalse(s.canUndo)
    }

    @Test
    fun draggingAnUnselectedNodeSelectsOnlyIt() {
        val s = state()
        s.select(NodeId("n1"))
        s.beginNodeDrag(NodeId("n3"))
        assertEquals(setOf(NodeId("n3")), s.selection)
    }

    @Test
    fun dragSnapsToTheGridAndCancelRestores() {
        val s = state(gridStep = 20f)
        s.beginNodeDrag(NodeId("n1"))
        s.dragNodesBy(Offset(27f, 9f))
        assertEquals(Offset(20f, 0f), s.dragDelta)
        s.cancelNodeDrag()
        assertEquals(Offset.Zero, s.dragDelta)
        assertEquals(Offset(0f, 0f), s.positionOf(s.graph.node(NodeId("n1"))!!))
        assertFalse(s.canUndo)
        s.beginNodeDrag(NodeId("n1")); s.dragNodesBy(Offset(31f, 31f)); s.endNodeDrag()
        assertEquals(Offset(40f, 40f), s.graph.node(NodeId("n1"))!!.position)
    }

    @Test
    fun aDragThatEndsWhereItStartedRecordsNothing() {
        val s = state()
        s.beginNodeDrag(NodeId("n1"))
        s.dragNodesBy(Offset(10f, 0f)); s.dragNodesBy(Offset(-10f, 0f))
        s.endNodeDrag()
        assertFalse(s.canUndo)
    }

    @Test
    fun aWireSnapsToANearbyCompatiblePortAndConnectsOnRelease() {
        val s = state()
        s.beginWire(ref("n1", "out"))
        val w0 = s.wire!!
        assertTrue(ref("n2", "a") in w0.compatible && ref("n2", "b") in w0.compatible)
        assertFalse(ref("n1", "a") in w0.compatible, "own node's ports are not offered")
        assertFalse(ref("n2", "out") in w0.compatible, "outputs are not offered")
        s.updateWire(Offset(300f + 5f, 40f + 4f))
        assertEquals(ref("n2", "a"), s.wire!!.target)
        val check = s.endWire()
        assertTrue(check is ConnectionCheck.Allowed)
        assertNull(s.wire)
        assertEquals(listOf("n1.out->n2.a"), s.graph.edges.keys.map { it.value })
    }

    @Test
    fun releasingAWireInTheMiddleOfNowhereDoesNothing() {
        val s = state()
        s.beginWire(ref("n1", "out"))
        s.updateWire(Offset(150f, 500f))
        assertNull(s.wire!!.target)
        assertNull(s.endWire())
        assertTrue(s.graph.edges.isEmpty())
        assertFalse(s.canUndo)
    }

    @Test
    fun cyclesAreNotOfferedAsTargets() {
        val s = state()
        s.connect(ref("n1", "out"), ref("n2", "a"))
        s.beginWire(ref("n2", "out"))
        assertFalse(ref("n1", "a") in s.wire!!.compatible, "would close a loop")
        assertTrue(ref("n3", "a") in s.wire!!.compatible)
        s.cancelWire()
        assertNull(s.wire)
    }

    @Test
    fun keyboardWiringConnectsTwoPorts() {
        val s = state()
        s.beginKeyboardWire(ref("n1", "out"))
        assertNotNull(s.wire)
        assertTrue(s.completeWire(ref("n3", "b")) is ConnectionCheck.Allowed)
        assertEquals(1, s.graph.edges.size)
        assertNull(s.completeWire(ref("n3", "a")), "no wire pending")
    }

    @Test
    fun deletingTheSelectionIsOneUndoStepAndDropsEdges() {
        val s = state()
        s.connect(ref("n1", "out"), ref("n2", "a"))
        s.connect(ref("n2", "out"), ref("n3", "a"))
        val before = s.graph
        s.select(NodeId("n2"))
        s.removeSelection()
        assertEquals(setOf(NodeId("n1"), NodeId("n3")), s.graph.nodes.keys)
        assertTrue(s.graph.edges.isEmpty())
        assertTrue(s.selection.isEmpty(), "selection forgets removed nodes")
        s.undo()
        assertEquals(before, s.graph)
    }

    @Test
    fun selectedEdgesCanBeDeleted() {
        val s = state()
        s.connect(ref("n1", "out"), ref("n2", "a"))
        s.selectEdge(EdgeId("n1.out->n2.a"))
        s.removeSelection()
        assertTrue(s.graph.edges.isEmpty())
        assertEquals(3, s.graph.nodes.size)
    }

    @Test
    fun changeCallbackFiresForEditsUndoAndRedo() {
        val s = state()
        val seen = mutableListOf<Int>()
        s.onGraphChange = { _, new -> seen += new.edges.size }
        s.connect(ref("n1", "out"), ref("n2", "a"))
        s.undo()
        s.redo()
        assertEquals(listOf(1, 0, 1), seen)
    }

    @Test
    fun rejectedConnectionsReportWhy() {
        val s = state()
        val check = s.connect(ref("n1", "out"), ref("n1", "a"))
        assertEquals(ConnectionCheck.Rejected(ConnectionRejection.SameNode), check)
    }

    @Test
    fun loadingReplacesTheGraphAndForgetsHistory() {
        val s = state()
        s.execute(GraphCommand.MoveNodes(mapOf(NodeId("n1") to Offset(5f, 5f))))
        s.select(NodeId("n1"))
        s.load(Graph.Empty)
        assertTrue(s.graph.nodes.isEmpty() && !s.canUndo && s.selection.isEmpty())
    }

    @Test
    fun removingANodeDropsItsAnchors() {
        val s = state()
        s.execute(GraphCommand.RemoveNodes(setOf(NodeId("n2"))))
        assertNull(s.anchorOf(ref("n2", "a")))
        assertNotNull(s.anchorOf(ref("n1", "a")))
    }

    @Test
    fun rectangleSelectionPicksNodesWhoseBoxesTouchIt() {
        val s = state()
        s.sizes[NodeId("n1")] = Size(200f, 100f); s.sizes[NodeId("n2")] = Size(200f, 100f); s.sizes[NodeId("n3")] = Size(200f, 100f)
        assertEquals(setOf(NodeId("n1"), NodeId("n2")), s.nodesIn(Rect(150f, 20f, 350f, 60f)))
        s.selectInRect(Rect(150f, 20f, 350f, 60f))
        assertEquals(setOf(NodeId("n1"), NodeId("n2")), s.selection)
        s.selectInRect(Rect(650f, 0f, 700f, 10f), additive = true)
        assertEquals(setOf(NodeId("n1"), NodeId("n2"), NodeId("n3")), s.selection)
        s.selectInRect(Rect(1000f, 1000f, 1100f, 1100f))
        assertTrue(s.selection.isEmpty())
    }

    @Test
    fun duplicatingCopiesNodesAndTheWiresBetweenThemOnly() {
        val s = state()
        s.connect(ref("n1", "out"), ref("n2", "a"))
        s.connect(ref("n2", "out"), ref("n3", "a"))
        s.select(NodeId("n1")); s.select(NodeId("n2"), additive = true)
        s.duplicateSelection(Offset(10f, 20f))
        assertEquals(5, s.graph.nodes.size)
        val copies = s.selection
        assertEquals(setOf(NodeId("n1_2"), NodeId("n2_2")), copies, "the copies end up selected")
        assertEquals(Offset(10f, 20f), s.graph.node(NodeId("n1_2"))!!.position)
        assertEquals(Offset(310f, 20f), s.graph.node(NodeId("n2_2"))!!.position)
        assertTrue(s.graph.edges.values.any { it.from == ref("n1_2", "out") && it.to == ref("n2_2", "a") }, "inner wire copied")
        assertEquals(3 + 0, s.graph.edges.size - 0 - 0 + 0 - 0 + 0, "originals 2 + one copied wire")
        assertFalse(s.graph.edges.values.any { it.from.node == NodeId("n2_2") && it.to.node == NodeId("n3") }, "wires leaving the selection are not copied")
        s.undo()
        assertEquals(3, s.graph.nodes.size)
        assertEquals(2, s.graph.edges.size)
    }

    @Test
    fun pasteUsesTheClipboardAndKeepsIdsUnique() {
        val s = state()
        assertFalse(s.canPaste)
        s.paste()
        assertEquals(3, s.graph.nodes.size)
        s.select(NodeId("n1"))
        s.copySelection()
        assertTrue(s.canPaste)
        s.paste(); s.paste()
        assertEquals(setOf("n1", "n2", "n3", "n1_2", "n1_3"), s.graph.nodes.keys.map { it.value }.toSet())
        assertEquals(setOf(NodeId("n1_3")), s.selection)
    }

    @Test
    fun copyingNothingKeepsTheOldClipboard() {
        val s = state()
        s.select(NodeId("n1")); s.copySelection()
        s.clearSelection(); s.copySelection()
        assertTrue(s.canPaste)
    }
}
