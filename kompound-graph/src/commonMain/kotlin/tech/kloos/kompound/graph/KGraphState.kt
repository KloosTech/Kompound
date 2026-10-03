package tech.kloos.kompound.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import tech.kloos.kompound.graph.model.ConnectionCheck
import tech.kloos.kompound.graph.model.ConnectionPolicy
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphDocument
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortRef
import kotlin.math.roundToInt

/**
 * A wire being dragged out of a port.
 *
 * @property from The port the wire started at.
 * @property pointer Where the pointer is, in world coordinates.
 * @property target The compatible port the wire would snap to if released now, or `null`.
 * @property compatible Every port the wire may legally end at (for highlighting).
 */
@Immutable
public class KWireDraft(
    public val from: PortRef,
    public val pointer: Offset,
    public val target: PortRef?,
    public val compatible: Set<PortRef>,
)

/**
 * State of a node graph editor: the [graph] with undo and redo, the selection, the [viewport] and the interactions in
 * progress (dragging nodes, dragging a wire). Create it with [rememberKGraphState].
 *
 * Every change to the graph goes through [execute] / [connect] so it can be undone. Moving a node by dragging is
 * one command, committed on release. State is read by the editor in the draw and layout phases where possible, so
 * panning and zooming do not recompose nodes.
 *
 * Use it from the main thread.
 *
 * @param initial The starting graph.
 * @param policy Which ports may be connected.
 * @param viewport Pan and zoom.
 * @param gridStep Initial snap step in world units for dragged nodes; `0` turns snapping off.
 * @param wireSnapRadius How close (world units) a dragged wire must come to a port to snap to it.
 * @param maxHistory Undo steps kept.
 */
@Stable
public class KGraphState(
    initial: Graph = Graph.Empty,
    policy: ConnectionPolicy = ConnectionPolicy(),
    public val viewport: KViewportState = KViewportState(),
    gridStep: Float = 0f,
    public val wireSnapRadius: Float = 28f,
    maxHistory: Int = 200,
) {
    private val document = GraphDocument(initial, policy, maxHistory)

    /** Snap step in world units for dragged nodes; `0` turns snapping off. May be changed at any time. */
    public var gridStep: Float by mutableFloatStateOf(gridStep)

    /** The connection rules in force. */
    public val policy: ConnectionPolicy get() = document.policy

    /** The current graph. */
    public var graph: Graph by mutableStateOf(initial)
        private set

    /** Selected nodes. */
    public var selection: Set<NodeId> by mutableStateOf(emptySet())
        private set

    /** Selected edges. */
    public var selectedEdges: Set<EdgeId> by mutableStateOf(emptySet())
        private set

    /** Whether there is something to undo. */
    public var canUndo: Boolean by mutableStateOf(false)
        private set

    /** Whether there is something to redo. */
    public var canRedo: Boolean by mutableStateOf(false)
        private set

    /** Called after every change of the graph with the old and the new graph (also after undo and redo). */
    public var onGraphChange: ((old: Graph, new: Graph) -> Unit)? = null

    // --- editing ------------------------------------------------------------------------------------------------

    /** Applies [command]; `false` when it changed nothing. */
    public fun execute(command: GraphCommand): Boolean {
        val old = document.graph
        val changed = document.execute(command)
        if (changed) sync(old)
        return changed
    }

    /** Connects [a] and [b] if the policy allows; returns the verdict. */
    public fun connect(a: PortRef, b: PortRef): ConnectionCheck {
        val old = document.graph
        val check = document.connect(a, b)
        if (check is ConnectionCheck.Allowed) sync(old)
        return check
    }

    /** Undoes the last change. */
    public fun undo() {
        val old = document.graph
        if (document.undo()) sync(old)
    }

    /** Redoes the last undone change. */
    public fun redo() {
        val old = document.graph
        if (document.redo()) sync(old)
    }

    /** Replaces the whole graph (loading a file); clears the history and the selection. */
    public fun load(graph: Graph) {
        val old = document.graph
        document.reset(graph)
        selection = emptySet()
        selectedEdges = emptySet()
        sync(old)
    }

    /** Deletes the selected nodes and edges as one undo step. */
    public fun removeSelection() {
        val commands = buildList {
            if (selectedEdges.isNotEmpty()) add(GraphCommand.Disconnect(selectedEdges))
            if (selection.isNotEmpty()) add(GraphCommand.RemoveNodes(selection))
        }
        if (commands.isNotEmpty()) execute(GraphCommand.Batch(commands, "Delete"))
    }

    private fun sync(old: Graph) {
        val g = document.graph
        graph = g
        canUndo = document.canUndo
        canRedo = document.canRedo
        selection = selection.filterTo(HashSet()) { it in g.nodes }
        selectedEdges = selectedEdges.filterTo(HashSet()) { it in g.edges }
        anchors.keys.filter { it.node !in g.nodes }.forEach { anchors.remove(it) }
        sizes.keys.filter { it !in g.nodes }.forEach { sizes.remove(it) }
        if (old != g) onGraphChange?.invoke(old, g)
    }

    // --- selection ----------------------------------------------------------------------------------------------

    /** Selects [id]; with [additive] it toggles it in the current selection instead of replacing it. */
    public fun select(id: NodeId, additive: Boolean = false) {
        if (id !in graph.nodes) return
        selectedEdges = emptySet()
        selection = if (additive) (if (id in selection) selection - id else selection + id) else setOf(id)
    }

    /** Selects the edge [id]; with [additive] it toggles. */
    public fun selectEdge(id: EdgeId, additive: Boolean = false) {
        if (id !in graph.edges) return
        selection = if (additive) selection else emptySet()
        selectedEdges = if (additive) (if (id in selectedEdges) selectedEdges - id else selectedEdges + id) else setOf(id)
    }

    /** Selects every node. */
    public fun selectAll() {
        selection = graph.nodes.keys.toSet()
        selectedEdges = emptySet()
    }

    /** Clears the selection. */
    public fun clearSelection() {
        selection = emptySet()
        selectedEdges = emptySet()
    }

    // --- dragging nodes -----------------------------------------------------------------------------------------

    private var dragRaw: Offset = Offset.Zero
    private var dragPrimary: NodeId? = null
    private var dragSet: Set<NodeId> = emptySet()

    /** Offset in world units currently applied to the nodes being dragged (already snapped to the grid). */
    public var dragDelta: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Whether nodes are being dragged. */
    public val isDraggingNodes: Boolean get() = dragPrimary != null

    /** Starts dragging with [id] as the grabbed node; a node outside the selection becomes the whole selection. */
    public fun beginNodeDrag(id: NodeId) {
        if (id !in graph.nodes) return
        if (id !in selection) select(id)
        dragPrimary = id
        dragSet = selection
        dragRaw = Offset.Zero
        dragDelta = Offset.Zero
    }

    /** Moves the dragged nodes by [delta] more world units. */
    public fun dragNodesBy(delta: Offset) {
        val primary = dragPrimary?.let { graph.node(it) } ?: return
        dragRaw += delta
        dragDelta = if (gridStep > 0f) {
            val target = primary.position + dragRaw
            Offset(snap(target.x), snap(target.y)) - primary.position
        } else dragRaw
    }

    /** Commits the drag as one undoable move. */
    public fun endNodeDrag() {
        val delta = dragDelta
        val set = dragSet
        resetDrag()
        if (delta != Offset.Zero) execute(GraphCommand.MoveNodes(set.associateWith { delta }))
    }

    /** Abandons the drag; nodes return to where they were. */
    public fun cancelNodeDrag() {
        resetDrag()
    }

    private fun resetDrag() {
        dragPrimary = null
        dragSet = emptySet()
        dragRaw = Offset.Zero
        dragDelta = Offset.Zero
    }

    /** Where [node] is drawn: its position plus the drag offset while it is being dragged. */
    public fun positionOf(node: GraphNode): Offset = if (node.id in dragSet) node.position + dragDelta else node.position

    private fun snap(v: Float): Float = (v / gridStep).roundToInt() * gridStep

    // --- wiring -------------------------------------------------------------------------------------------------

    /** The wire being dragged, or `null`. */
    public var wire: KWireDraft? by mutableStateOf(null)
        private set

    /** World positions of the port centres, reported by the ports themselves once they are laid out. */
    internal val anchors = mutableStateMapOf<PortRef, Offset>()

    /** Measured sizes of the nodes in world units. */
    internal val sizes = mutableStateMapOf<NodeId, Size>()

    /** Coordinates of the world layer; ports measure their centre relative to it. Set by the editor. */
    internal var layer: LayoutCoordinates? = null

    /** Bumped when [layer] changes so ports re-report their anchors. */
    internal var layerTick: Int by mutableIntStateOf(0)

    /** Size of the canvas in pixels; [Size.Zero] before it was laid out. */
    public var canvasSize: Size = Size.Zero
        internal set

    /** Records where the centre of a port is, from its layout coordinates. */
    internal fun reportPort(ref: PortRef, coordinates: LayoutCoordinates) {
        val l = layer ?: return
        if (!l.isAttached || !coordinates.isAttached) return
        val centre = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
        val world = l.localPositionOf(coordinates, centre)
        if (anchors[ref] != world) anchors[ref] = world
    }

    /** Zooms and pans so every node is visible. */
    public fun fitView(padding: Float = 48f) {
        val rects = graph.nodes.values.map { n ->
            val size = sizes[n.id] ?: Size(220f, 120f)
            Rect(n.position, size)
        }
        if (rects.isEmpty() || canvasSize == Size.Zero) return
        val bounds = Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
        viewport.fit(bounds, canvasSize, padding)
    }

    /** World position of the centre of the port [ref], or `null` before it was laid out. */
    public fun anchorOf(ref: PortRef): Offset? = anchors[ref]

    /** Starts a wire at the port [from]; the pointer starts on the port. */
    public fun beginWire(from: PortRef) {
        val start = anchors[from] ?: return
        val compatible = graph.nodes.values.flatMap { n -> n.ports.map { PortRef(n.id, it.id) } }
            .filterTo(HashSet()) { it != from && policy.check(graph, from, it) is ConnectionCheck.Allowed }
        wire = KWireDraft(from, start, null, compatible)
    }

    /** Moves the loose end of the wire to [pointer] (world coordinates), snapping to a compatible port nearby. */
    public fun updateWire(pointer: Offset) {
        val w = wire ?: return
        val target = w.compatible
            .mapNotNull { ref -> anchors[ref]?.let { ref to (it - pointer).getDistance() } }
            .filter { it.second <= wireSnapRadius }
            .minByOrNull { it.second }?.first
        wire = KWireDraft(w.from, pointer, target, w.compatible)
    }

    /** Drops the wire: connects when it snapped to a compatible port, otherwise nothing happens. Returns the verdict if it tried. */
    public fun endWire(): ConnectionCheck? {
        val w = wire ?: return null
        wire = null
        return w.target?.let { connect(w.from, it) }
    }

    /** Throws the wire away. */
    public fun cancelWire() {
        wire = null
    }

    /** Keyboard wiring: starts a wire at [from] without a pointer. */
    public fun beginKeyboardWire(from: PortRef) {
        beginWire(from)
    }

    /** Keyboard wiring: finishes the wire at [target]; returns the verdict, or `null` when no wire is pending. */
    public fun completeWire(target: PortRef): ConnectionCheck? {
        val w = wire ?: return null
        wire = null
        return connect(w.from, target)
    }
}

/** Remembers a [KGraphState] with the given starting graph; pan and zoom survive configuration changes (the graph does not: persist it yourself). */
@Composable
public fun rememberKGraphState(
    initial: Graph = Graph.Empty,
    policy: ConnectionPolicy = ConnectionPolicy(),
    gridStep: Float = 0f,
): KGraphState {
    val viewport = rememberSaveable(saver = KViewportState.Saver) { KViewportState() }
    return remember(viewport) { KGraphState(initial, policy, viewport, gridStep) }
}
