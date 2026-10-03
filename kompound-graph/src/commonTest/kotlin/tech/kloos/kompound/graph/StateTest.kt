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
import tech.kloos.kompound.graph.model.Subgraphs
import tech.kloos.kompound.graph.model.PortSpec
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

    @Test
    fun draggingSnapsToAnotherNodesEdgeAndShowsAGuide() {
        val s = state()
        for (id in listOf("n1", "n2", "n3")) s.sizes[NodeId(id)] = Size(200f, 100f)
        s.snapToNodes = true
        s.beginNodeDrag(NodeId("n1"))
        // n1 at x 0..200; n2 starts at x=300. Moving n1 by 97 puts its right edge (297) within 6 of n2's left edge (300).
        s.dragNodesBy(Offset(97f, 0f))
        assertEquals(100f, s.dragDelta.x, 0.001f, "snapped so that the right edge meets n2's left edge")
        assertTrue(s.guides.any { it.vertical && abs(it.position - 300f) < 0.5f })
        // top edges are all at y=0 already: a horizontal guide at 0 appears too
        assertTrue(s.guides.any { !it.vertical && abs(it.position) < 0.5f })
        s.dragNodesBy(Offset(-60f, 40f))
        assertTrue(s.guides.none { it.vertical }, "too far from any vertical alignment")
        s.endNodeDrag()
        assertTrue(s.guides.isEmpty())
    }

    @Test
    fun nodeSnappingIsOffByDefaultAndCentresAlignToo() {
        val s = state()
        for (id in listOf("n1", "n2", "n3")) s.sizes[NodeId(id)] = Size(200f, 100f)
        s.beginNodeDrag(NodeId("n1"))
        s.dragNodesBy(Offset(97f, 0f))
        assertEquals(97f, s.dragDelta.x)
        assertTrue(s.guides.isEmpty())
        s.cancelNodeDrag()
        s.snapToNodes = true
        s.execute(GraphCommand.MoveNodes(mapOf(NodeId("n1") to Offset(0f, 300f))))
        s.beginNodeDrag(NodeId("n1"))
        // n2's vertical centre is y=50; n1 (height 100) centre would be 350+dy: moving up by 297 puts it at 53, within 6 of 50
        s.dragNodesBy(Offset(0f, -297f))
        assertEquals(-300f, s.dragDelta.y, 0.001f)
    }

    @Test
    fun insertingARerouteSplitsTheWireIntoTwoInOneUndoStep() {
        val s = state()
        s.connect(ref("n1", "out"), ref("n2", "a"))
        val reroute = s.insertReroute(EdgeId("n1.out->n2.a"), Offset(150f, 50f))!!
        assertEquals(4, s.graph.nodes.size)
        assertEquals(KRerouteKind, s.graph.node(reroute)!!.kind)
        assertEquals(setOf("n1.out->reroute_1.in", "reroute_1.out->n2.a"), s.graph.edges.keys.map { it.value }.toSet())
        assertEquals(setOf(reroute), s.selection)
        s.undo()
        assertEquals(3, s.graph.nodes.size)
        assertEquals(listOf("n1.out->n2.a"), s.graph.edges.keys.map { it.value })
        assertNull(s.insertReroute(EdgeId("ghost"), Offset.Zero))
    }

    @Test
    fun addingANodeFromAWireConnectsItsFirstCompatiblePortInOneUndoStep() {
        val s = state()
        val type = KNodeType("calc", "Calc", listOf(PortSpec.output("o"), PortSpec.input("i1"), PortSpec.input("i2")))
        val node = s.addNode(type, Offset(400f, 400f), connectFrom = ref("n1", "out"))
        assertEquals("calc_4", node.id.value)
        assertEquals(listOf("n1.out->calc_4.i1"), s.graph.edges.keys.map { it.value })
        assertEquals(setOf(node.id), s.selection)
        s.undo()
        assertEquals(3, s.graph.nodes.size)
        assertTrue(s.graph.edges.isEmpty())
    }

    @Test
    fun anAddedNodeWithNoCompatiblePortIsStillAddedWithoutAWire() {
        val s = state()
        val type = KNodeType("src", "Source", listOf(PortSpec.output("o")))
        val wired = s.addNode(GraphNodeOf(type), connectFrom = ref("n1", "out"))
        assertFalse(wired)
        assertEquals(4, s.graph.nodes.size)
        assertTrue(s.graph.edges.isEmpty())
    }

    private fun GraphNodeOf(type: KNodeType) = tech.kloos.kompound.graph.model.GraphNode(NodeId("x"), type.kind, Offset.Zero, type.ports)

    @Test
    fun droppingAWireOnEmptyCanvasAsksForTheMenuOnlyWhenNodeTypesAreEnabled() {
        val s = state()
        s.beginWire(ref("n1", "out")); s.updateWire(Offset(150f, 500f)); s.endWire()
        assertNull(s.menuRequest)
        s.nodeMenuEnabled = true
        s.beginWire(ref("n1", "out")); s.updateWire(Offset(150f, 500f)); s.endWire()
        val request = s.menuRequest!!
        assertEquals(ref("n1", "out"), request.from)
        assertEquals(Offset(150f, 500f), request.world)
        s.menuRequest = null
        // a barely moved wire (a tap on the port) does not
        s.beginWire(ref("n1", "out")); s.updateWire(Offset(205f, 45f)); s.endWire()
        assertNull(s.menuRequest)
    }
}

class KGraphGroupStateTest {
    private fun state(): KGraphState {
        val s = KGraphState(Graph.of(listOf(math("n1", Offset(0f, 0f)), math("n2", Offset(300f, 0f)), math("n3", Offset(600f, 0f)), math("n4", Offset(900f, 0f)))))
        for (id in listOf("n1", "n2", "n3", "n4")) s.sizes[NodeId(id)] = Size(200f, 100f)
        for (n in s.graph.nodes.values) {
            s.anchors[PortRef(n.id, tech.kloos.kompound.graph.model.PortId("out"))] = n.position + Offset(200f, 40f)
            s.anchors[PortRef(n.id, tech.kloos.kompound.graph.model.PortId("a"))] = n.position + Offset(0f, 40f)
        }
        return s
    }

    @Test
    fun groupingTheSelectionIsOneUndoStep() {
        val s = state()
        s.select(NodeId("n1")); s.select(NodeId("n2"), additive = true)
        val id = s.groupSelection("Inputs")!!
        assertEquals(listOf(NodeId("n1"), NodeId("n2")), s.graph.membersOf(id))
        assertEquals("Inputs", s.graph.group(id)!!.title)
        s.undo()
        assertTrue(s.graph.groups.isEmpty() && s.graph.nodes.values.all { it.group == null })
        assertNull(KGraphState().groupSelection(), "nothing selected, nothing grouped")
    }

    @Test
    fun theFrameFitsItsMembersWithPaddingAndATitleBar() {
        val s = state()
        s.select(NodeId("n1")); s.select(NodeId("n2"), additive = true)
        val id = s.groupSelection()!!
        val b = s.groupBounds(id)!!
        assertEquals(-GroupPadding, b.left); assertEquals(500f + GroupPadding, b.right)
        assertEquals(-GroupPadding - GroupHeader, b.top); assertEquals(100f + GroupPadding, b.bottom)
        assertEquals(b.topLeft, s.collapsedRect(id)!!.topLeft)
    }

    @Test
    fun collapsingHidesMembersLeavesTheSelectionAndSkipsThemInMarqueeAndSelectAll() {
        val s = state()
        s.select(NodeId("n1")); s.select(NodeId("n2"), additive = true)
        val id = s.groupSelection()!!
        s.toggleCollapsed(id)
        assertTrue(s.isHidden(s.graph.node(NodeId("n1"))!!))
        assertTrue(s.selection.isEmpty())
        s.selectAll()
        assertEquals(setOf(NodeId("n3"), NodeId("n4")), s.selection)
        assertEquals(setOf(NodeId("n3")), s.nodesIn(Rect(590f, 0f, 700f, 10f)))
        assertTrue(s.nodesIn(Rect(0f, 0f, 100f, 10f)).isEmpty(), "hidden nodes cannot be marquee selected")
        s.undo()
        assertFalse(s.graph.group(id)!!.collapsed)
    }

    @Test
    fun wiresIntoACollapsedGroupEndOnItsBoxAndInnerWiresDisappear() {
        val s = state()
        s.connect(ref("n1", "out"), ref("n2", "a"))
        s.connect(ref("n2", "out"), ref("n3", "a"))
        s.connect(ref("n3", "out"), ref("n1", "a")).let { assertTrue(it is ConnectionCheck.Rejected, "cycle") }
        s.select(NodeId("n1")); s.select(NodeId("n2"), additive = true)
        val id = s.groupSelection()!!
        s.toggleCollapsed(id)
        val box = s.collapsedRect(id)!!
        val resolved = s.resolvedEdges()
        assertEquals(1, resolved.size, "the wire between the two members is hidden")
        val r = resolved.single()
        assertEquals("n2.out->n3.a", r.edge.id.value)
        assertEquals(box.right, r.from.x, "leaves from the box's right edge")
        assertEquals(s.anchorOf(ref("n3", "a")), r.to)
        s.toggleCollapsed(id)
        assertEquals(2, s.resolvedEdges().size)
    }

    @Test
    fun wiresCannotTargetHiddenPorts() {
        val s = state()
        s.select(NodeId("n2")); s.groupSelection(); s.toggleCollapsed(s.graph.groups.keys.single())
        s.beginWire(ref("n1", "out"))
        assertFalse(ref("n2", "a") in s.wire!!.compatible)
        assertTrue(ref("n3", "a") in s.wire!!.compatible)
    }

    @Test
    fun draggingAGroupMovesAllItsMembersAsOneUndoStep() {
        val s = state()
        s.select(NodeId("n1")); s.select(NodeId("n2"), additive = true)
        val id = s.groupSelection()!!
        s.clearSelection()
        s.beginGroupDrag(id)
        s.dragNodesBy(Offset(40f, 20f))
        assertEquals(Offset(40f, 20f), s.positionOf(s.graph.node(NodeId("n1"))!!))
        assertEquals(Offset(340f, 20f), s.positionOf(s.graph.node(NodeId("n2"))!!))
        assertEquals(Offset(600f, 0f), s.positionOf(s.graph.node(NodeId("n3"))!!))
        assertEquals(s.groupBounds(id)!!.left, -GroupPadding + 40f, "the frame moves with the drag")
        s.endNodeDrag()
        s.undo()
        assertEquals(Offset(0f, 0f), s.graph.node(NodeId("n1"))!!.position)
    }

    @Test
    fun ungroupingRenamingAndPastingBehave() {
        val s = state()
        s.select(NodeId("n1")); val id = s.groupSelection()!!
        s.renameGroup(id, "Source")
        assertEquals("Source", s.graph.group(id)!!.title)
        s.selectGroup(id)
        assertEquals(setOf(NodeId("n1")), s.selection)
        s.duplicateSelection()
        assertNull(s.graph.node(NodeId("n1_2"))!!.group, "copies are not put into the group")
        s.select(NodeId("n1"))
        s.ungroupSelection()
        assertTrue(s.graph.groups.isEmpty())
        assertNull(s.graph.node(NodeId("n1"))!!.group)
    }

    @Test
    fun fitViewIncludesCollapsedGroupBoxes() {
        val s = state()
        s.canvasSize = Size(1000f, 600f)
        s.select(NodeId("n4")); val id = s.groupSelection()!!
        s.toggleCollapsed(id)
        s.fitView(padding = 0f)
        val visible = s.viewport.visibleWorld(s.canvasSize)
        assertTrue(visible.contains(s.collapsedRect(id)!!.topLeft))
    }

    @Test
    fun autoLayoutArrangesInOneUndoStepAndRespectsTheSelection() {
        val s = KGraphState(Graph.of(listOf(math("a", Offset(900f, 700f)), math("b", Offset(0f, 0f)), math("c", Offset(300f, 800f)), math("far", Offset(5000f, 5000f))),
            listOf(tech.kloos.kompound.graph.model.Edge(EdgeId("e1"), ref("a", "out"), ref("b", "a")), tech.kloos.kompound.graph.model.Edge(EdgeId("e2"), ref("b", "out"), ref("c", "a")))))
        for (n in s.graph.nodes.keys) s.sizes[n] = Size(200f, 100f)
        assertTrue(s.autoLayout())
        val a = s.graph.node(NodeId("a"))!!.position; val b = s.graph.node(NodeId("b"))!!.position; val c = s.graph.node(NodeId("c"))!!.position
        assertTrue(a.x < b.x && b.x < c.x, "a -> b -> c runs left to right: $a $b $c")
        assertFalse(s.autoLayout(), "a second run changes nothing")
        s.undo()
        assertEquals(Offset(900f, 700f), s.graph.node(NodeId("a"))!!.position)
        assertFalse(s.canUndo)
        s.select(NodeId("a")); s.select(NodeId("b"), additive = true)
        s.autoLayout(selectedOnly = true)
        assertEquals(Offset(300f, 800f), s.graph.node(NodeId("c"))!!.position, "unselected nodes stay")
    }

    @Test
    fun autoLayoutLeavesCollapsedGroupMembersAlone() {
        val s = state()
        s.sizes.putAll(listOf("n1", "n2", "n3").associate { NodeId(it) to Size(200f, 100f) })
        s.select(NodeId("n1")); s.groupSelection(); s.toggleCollapsed(s.graph.groups.keys.single())
        val before = s.graph.node(NodeId("n1"))!!.position
        s.autoLayout()
        assertEquals(before, s.graph.node(NodeId("n1"))!!.position)
    }
}

class KGraphSubgraphStateTest {
    private val num = tech.kloos.kompound.graph.model.PortType.of("number")
    private fun calc(id: String, at: Offset) = tech.kloos.kompound.graph.model.node(id, PortSpec.input("a", "A", num), PortSpec.input("b", "B", num), PortSpec.output("out", "Result", num), at = at)
    private fun src(id: String, at: Offset) = tech.kloos.kompound.graph.model.node(id, PortSpec.output("out", "Value", num), at = at)

    private fun state(): KGraphState {
        val g = Graph.of(
            listOf(src("s1", Offset(0f, 0f)), src("s2", Offset(0f, 150f)), calc("add", Offset(300f, 50f)), calc("mul", Offset(600f, 80f)), tech.kloos.kompound.graph.model.node("sink", PortSpec.input("in", type = num), at = Offset(900f, 80f))),
            listOf(
                Edge(EdgeId("e1"), ref("s1", "out"), ref("add", "a")), Edge(EdgeId("e2"), ref("s2", "out"), ref("add", "b")),
                Edge(EdgeId("e3"), ref("add", "out"), ref("mul", "a")), Edge(EdgeId("e4"), ref("mul", "out"), ref("sink", "in")),
            ),
        )
        val s = KGraphState(g)
        for (n in g.nodes.keys) s.sizes[n] = Size(200f, 100f)
        return s
    }

    private fun wrapAddMul(s: KGraphState): NodeId {
        s.select(NodeId("add")); s.select(NodeId("mul"), additive = true)
        return s.createSubgraph("Calc")!!
    }

    @Test
    fun creatingASubgraphFromTheSelectionSelectsItAndHidesNothingAtTheTopLevel() {
        val s = state()
        val id = wrapAddMul(s)
        assertEquals(setOf(id), s.selection)
        assertEquals(null, s.scope)
        assertTrue(s.isHidden(s.graph.node(NodeId("add"))!!), "the content belongs to the subgraph")
        assertFalse(s.isHidden(s.graph.node(id)!!))
        s.selectAll()
        assertEquals(setOf("s1", "s2", "sink", id.value), s.selection.map { it.value }.toSet())
        for (n in s.graph.nodes.values) for (p in n.ports) s.anchors[PortRef(n.id, p.id)] = n.position   // the editor reports these once laid out
        assertEquals(3, s.resolvedEdges().size, "s1->calc, s2->calc, calc->sink")
        s.undo()
        assertEquals(5, s.graph.nodes.size)
    }

    @Test
    fun enteringAndLeavingSwitchesWhatIsVisibleAndClearsTheSelection() {
        val s = state()
        val id = wrapAddMul(s)
        s.enterSubgraph(id)
        assertEquals(id, s.scope)
        assertEquals(listOf(id), s.scopePath)
        assertTrue(s.selection.isEmpty())
        assertFalse(s.isHidden(s.graph.node(NodeId("add"))!!))
        assertTrue(s.isHidden(s.graph.node(NodeId("s1"))!!))
        s.selectAll()
        assertTrue(NodeId("s1") !in s.selection && NodeId("add") in s.selection)
        assertTrue(s.resolvedEdges().all { r -> s.graph.node(r.edge.from.node)!!.scope == id && s.graph.node(r.edge.to.node)!!.scope == id })
        assertEquals("Calc", s.subgraphTitle(id))
        assertTrue(s.exitSubgraph())
        assertEquals(null, s.scope)
        assertFalse(s.exitSubgraph())
        s.enterSubgraph(NodeId("add"))   // not a subgraph node
        assertEquals(null, s.scope)
    }

    @Test
    fun wiresAndMarqueeOnlySeeTheOpenLevel() {
        val s = state()
        val id = wrapAddMul(s)
        s.enterSubgraph(id)
        for (n in s.graph.nodes.values) if (n.scope == id) { for (p in n.ports) s.anchors[PortRef(n.id, p.id)] = n.position }
        s.beginWire(ref("add", "out"))
        assertTrue(s.wire!!.compatible.all { s.graph.node(it.node)!!.scope == id }, "no ports of other levels are offered")
        s.cancelWire()
        assertTrue(s.nodesIn(Rect(-1000f, -1000f, 5000f, 5000f)).all { s.graph.node(it)!!.scope == id })
    }

    @Test
    fun undoingTheCreationWhileInsideLeavesTheSubgraph() {
        val s = state()
        val id = wrapAddMul(s)
        s.enterSubgraph(id)
        s.undo()
        assertEquals(null, s.scope)
        assertTrue(s.scopePath.isEmpty())
        s.redo()
        assertEquals(null, s.scope, "redo does not reopen the subgraph")
    }

    @Test
    fun breadcrumbsFollowNestedLevelsAndExitToJumpsUp() {
        val s = state()
        val outer = wrapAddMul(s)
        s.enterSubgraph(outer)
        s.select(NodeId("add"))
        val inner = s.createSubgraph("Inner")!!
        s.enterSubgraph(inner)
        assertEquals(listOf(outer, inner), s.scopePath)
        s.exitTo(1)
        assertEquals(listOf(outer), s.scopePath)
        s.exitTo(0)
        assertTrue(s.scopePath.isEmpty())
        s.exitTo(5)
        assertTrue(s.scopePath.isEmpty())
    }

    @Test
    fun duplicatingASubgraphCopiesItsContentAndKeepsTheCopyIndependent() {
        val s = state()
        val id = wrapAddMul(s)
        s.duplicateSelection(Offset(0f, 400f))
        val copy = s.graph.nodes.values.single { it.kind == Subgraphs.Kind && it.id != id }
        assertEquals("subgraph_2", s.graph.nodes.keys.map { it.value }.first { it.startsWith("subgraph_") && it != id.value && !it.contains("/") })
        assertEquals(setOf(copy.id), s.selection, "only the copy at the visible level is selected")
        val inside = s.graph.nodes.values.filter { it.scope == copy.id }
        assertEquals(s.graph.nodes.values.count { it.scope == id }, inside.size, "everything inside came along")
        assertTrue(inside.filter { Subgraphs.isBoundary(it) }.all { it.id.value.startsWith("${copy.id}/") }, "boundary ids follow the new id")
        assertTrue(s.graph.edges.values.any { it.from.node == NodeId("add_2") && it.to.node == NodeId("mul_2") }, "inner wires were copied")
        assertFalse(s.graph.edges.values.any { (it.from.node.value.endsWith("_2") && it.to.node.value.startsWith("mul") && !it.to.node.value.endsWith("_2")) }, "no wire between original and copy")
        s.undo()
        assertEquals(9, s.graph.nodes.size, "the 5 originals, the subgraph node and its 3 boundary nodes")
    }

    @Test
    fun deletingASubgraphWithTheKeyboardRemovesItsContentAndUndoBringsItBack() {
        val s = state()
        val id = wrapAddMul(s)
        val wrapped = s.graph
        s.select(id)
        s.removeSelection()
        assertEquals(3, s.graph.nodes.size)
        s.undo()
        assertEquals(wrapped, s.graph)
    }

    @Test
    fun dissolvingSelectsTheFreedNodes() {
        val s = state()
        val id = wrapAddMul(s)
        s.dissolveSubgraph(id)
        assertEquals(setOf(NodeId("add"), NodeId("mul")), s.selection)
        assertEquals(5, s.graph.nodes.size)
    }

    @Test
    fun theViewportOfEachLevelIsRemembered() {
        val s = state()
        s.canvasSize = Size(1000f, 600f)
        val id = wrapAddMul(s)
        s.viewport.set(Offset(123f, 45f), 1f)
        s.enterSubgraph(id)
        s.viewport.set(Offset(7f, 8f), 2f)
        s.exitSubgraph()
        assertEquals(Offset(123f, 45f), s.viewport.offset)
        s.enterSubgraph(id)
        assertEquals(Offset(7f, 8f), s.viewport.offset)
        assertEquals(2f, s.viewport.zoom)
    }
}
