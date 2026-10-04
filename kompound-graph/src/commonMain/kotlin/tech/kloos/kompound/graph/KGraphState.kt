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
import tech.kloos.kompound.graph.layout.GraphLayout
import tech.kloos.kompound.graph.layout.LayoutOptions
import tech.kloos.kompound.graph.model.ConnectionCheck
import tech.kloos.kompound.graph.model.ConnectionPolicy
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphDocument
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.GroupId
import tech.kloos.kompound.graph.model.NodeGroup
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.Subgraphs
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
 * An alignment guide shown while dragging: a vertical line at world x [position] (when [vertical]) or a horizontal line at world y,
 * running from [start] to [end] along the other axis.
 */
@Immutable
public class KGuide(public val vertical: Boolean, public val position: Float, public val start: Float, public val end: Float)

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
        if (readOnly) return
        val old = document.graph
        if (document.undo()) sync(old)
    }

    /** Redoes the last undone change. */
    public fun redo() {
        if (readOnly) return
        val old = document.graph
        if (document.redo()) sync(old)
    }

    /** Replaces the whole graph (loading a file); clears the history and the selection. */
    public fun load(graph: Graph) {
        val old = document.graph
        document.reset(graph)
        scopePath = emptyList()
        savedViews.clear()
        selection = emptySet()
        selectedEdges = emptySet()
        sync(old)
    }

    /** Deletes the selected nodes and edges as one undo step. */
    public fun removeSelection() {
        if (readOnly) return
        val commands = buildList {
            if (selectedEdges.isNotEmpty()) add(GraphCommand.Disconnect(selectedEdges))
            if (selection.isNotEmpty()) add(GraphCommand.RemoveNodes(selection))
        }
        if (commands.isNotEmpty()) execute(GraphCommand.Batch(commands, "Delete"))
    }

    private fun sync(old: Graph) {
        val g = document.graph
        graph = g
        if (scopePath.any { it !in g.nodes }) scopePath = scopePath.takeWhile { it in g.nodes }
        canUndo = document.canUndo
        canRedo = document.canRedo
        selection = selection.filterTo(HashSet()) { it in g.nodes }
        selectedEdges = selectedEdges.filterTo(HashSet()) { it in g.edges }
        anchors.keys.filter { it.node !in g.nodes }.forEach { anchors.remove(it) }
        sizes.keys.filter { it !in g.nodes }.forEach { sizes.remove(it) }
        uiExpanded.keys.filter { key -> NodeId(key.substringBefore('/')) !in g.nodes }.forEach { uiExpanded.remove(it) }
        if (old != g) onGraphChange?.invoke(old, g)
    }

    // --- subgraphs ------------------------------------------------------------------------------------------

    /** The subgraph node that is open (its content fills the canvas), or `null` at the top level. */
    public val scope: NodeId? get() = scopePath.lastOrNull()

    /** The open subgraph nodes from the outermost to the innermost; empty at the top level. */
    public var scopePath: List<NodeId> by mutableStateOf(emptyList())
        private set

    private val savedViews = HashMap<NodeId?, Pair<Offset, Float>>()

    private fun switchScope(path: List<NodeId>) {
        if (path == scopePath) return
        savedViews[scope] = viewport.offset to viewport.zoom
        cancelWire(); cancelNodeDrag()
        scopePath = path
        clearSelection()
        savedViews[scope]?.let { (offset, zoom) -> viewport.set(offset, zoom) } ?: fitViewSoon()
    }

    private var pendingFit = false
    private fun fitViewSoon() { pendingFit = true }

    /** Called by the editor once the nodes of the new level were measured, to frame a level that was never shown before. */
    internal fun applyPendingFit() {
        if (pendingFit && canvasSize != Size.Zero && graph.nodes.values.filter { !isHidden(it) }.all { it.id in sizes }) {
            pendingFit = false
            fitView()
        }
    }

    /** Opens the subgraph node [id] (it must be shown at the current level). */
    public fun enterSubgraph(id: NodeId) {
        val node = graph.node(id) ?: return
        if (!Subgraphs.isSubgraph(node) || node.scope != scope) return
        switchScope(scopePath + id)
    }

    /** Goes up one level; `false` at the top level. */
    public fun exitSubgraph(): Boolean {
        if (scopePath.isEmpty()) return false
        switchScope(scopePath.dropLast(1))
        return true
    }

    /** Goes up to the level with [depth] open subgraphs (0 = top level). */
    public fun exitTo(depth: Int) {
        if (depth in 0 until scopePath.size) switchScope(scopePath.take(depth))
    }

    /** Title of the subgraph node [id] (its data when that is text, else its id). */
    public fun subgraphTitle(id: NodeId): String = graph.node(id)?.data as? String ?: id.value

    /** Wraps the selected nodes in a new subgraph node and returns its id (one undo step); `null` when nothing can be wrapped. */
    public fun createSubgraph(title: String = "Subgraph"): NodeId? {
        if (readOnly) return null
        var n = graph.nodes.size + 1
        while (NodeId("subgraph_$n") in graph.nodes) n++
        val id = NodeId("subgraph_$n")
        val command = Subgraphs.create(graph, selection, id, title) ?: return null
        if (!execute(command)) return null
        select(id)
        return id
    }

    /** Opens the subgraph node [id] up: its content moves to its level and the wires are joined (one undo step). */
    public fun dissolveSubgraph(id: NodeId) {
        if (readOnly) return
        val command = Subgraphs.dissolve(graph, id) ?: return
        val inner = graph.nodes.values.filter { it.scope == id && !Subgraphs.isBoundary(it) }.map { it.id }.toSet()
        if (execute(command)) setSelection(inner)
    }

    /** Adds an input port to the subgraph node [id] (with its boundary node inside). */
    public fun addSubgraphInput(id: NodeId, label: String, type: tech.kloos.kompound.graph.model.PortType = tech.kloos.kompound.graph.model.PortType.Any) {
        Subgraphs.addInput(graph, id, label, type)?.let { execute(it) }
    }

    /** Adds an output port to the subgraph node [id] (with its boundary node inside). */
    public fun addSubgraphOutput(id: NodeId, label: String, type: tech.kloos.kompound.graph.model.PortType = tech.kloos.kompound.graph.model.PortType.Any) {
        Subgraphs.addOutput(graph, id, label, type)?.let { execute(it) }
    }

    // --- groups --------------------------------------------------------------------------------------------

    /** Whether [node] is not shown at the moment: it belongs to a collapsed group, or to another subgraph than the one open. */
    public fun isHidden(node: GraphNode): Boolean = node.scope != scope || node.group?.let { graph.group(it)?.collapsed } == true

    /** Frame around the members of [id] (padded, with room for the title bar on top), or `null` for a group without members. */
    public fun groupBounds(id: GroupId): Rect? {
        val rects = graph.nodes.values.filter { it.group == id }.map { Rect(positionOf(it), sizes[it.id] ?: Size(220f, 120f)) }
        if (rects.isEmpty()) return null
        return Rect(
            rects.minOf { it.left } - GroupPadding, rects.minOf { it.top } - GroupPadding - GroupHeader,
            rects.maxOf { it.right } + GroupPadding, rects.maxOf { it.bottom } + GroupPadding,
        )
    }

    /** The compact box a collapsed group is drawn as: at the top-left of its expanded frame. */
    public fun collapsedRect(id: GroupId): Rect? = groupBounds(id)?.let { Rect(it.topLeft, CollapsedSize) }

    /** Wraps the selected nodes in a new group (one undo step). Returns its id, or `null` when nothing is selected. */
    public fun groupSelection(title: String = "Group"): GroupId? {
        if (readOnly) return null
        val members = selection.filter { graph.node(it) != null }
        if (members.isEmpty()) return null
        var n = graph.groups.size + 1
        while (GroupId("group_$n") in graph.groups) n++
        val id = GroupId("group_$n")
        val group = NodeGroup(id, title, collapsed = false, color = graph.groups.size % 7)
        execute(GraphCommand.Batch(listOf(GraphCommand.PutGroup(group), GraphCommand.AssignGroups(members.associateWith { id })), "Group"))
        return id
    }

    /** Dissolves the groups of the selected nodes; the nodes stay (one undo step). */
    public fun ungroupSelection() {
        if (readOnly) return
        val groups = selection.mapNotNull { graph.node(it)?.group }.toSet()
        if (groups.isNotEmpty()) execute(GraphCommand.Batch(groups.map { GraphCommand.RemoveGroup(it) }, "Ungroup"))
    }

    /** Dissolves the group [id]; its nodes stay. */
    public fun ungroup(id: GroupId) {
        if (readOnly) return
        execute(GraphCommand.RemoveGroup(id))
    }

    /** Collapses or expands the group [id]; its members leave the selection when it collapses. */
    public fun toggleCollapsed(id: GroupId) {
        if (readOnly) return
        val g = graph.group(id) ?: return
        execute(GraphCommand.PutGroup(g.copy(collapsed = !g.collapsed)))
        if (!g.collapsed) setSelection(selection - graph.membersOf(id).toSet())
    }

    /** Changes the title of the group [id]. */
    public fun renameGroup(id: GroupId, title: String) {
        if (readOnly) return
        graph.group(id)?.let { execute(GraphCommand.PutGroup(it.copy(title = title))) }
    }

    /** Selects all members of the group [id]. */
    public fun selectGroup(id: GroupId) {
        setSelection(graph.membersOf(id).toSet())
    }

    /** Starts dragging the whole group [id] (all its members move together). */
    public fun beginGroupDrag(id: GroupId) {
        if (readOnly) return
        val members = graph.membersOf(id)
        if (members.isEmpty()) return
        setSelection(members.toSet())
        beginNodeDrag(members.first())
    }

    /** Removes every undo and redo step (for example after laying out a freshly loaded graph). */
    public fun clearHistory() {
        document.clearHistory()
        canUndo = false
        canRedo = false
    }

    /** A wire from `from` to `to` with the points the editor draws it between; wires into or out of collapsed groups end on the group's box. */
    internal class ResolvedEdge(val edge: tech.kloos.kompound.graph.model.Edge, val from: Offset, val to: Offset)

    private fun nextRank(ranks: HashMap<Pair<GroupId, Boolean>, Int>, key: Pair<GroupId, Boolean>): Int {
        val rank = ranks[key] ?: 0
        ranks[key] = rank + 1
        return rank
    }

    /** Resolves every wire to drawable end points; wires inside one collapsed group are left out. */
    internal fun resolvedEdges(view: Rect? = null): List<ResolvedEdge> {
        val out = ArrayList<ResolvedEdge>(graph.edges.size)
        val ranks = HashMap<Pair<GroupId, Boolean>, Int>()
        val rects = HashMap<GroupId, Rect?>()
        fun collapsedOf(node: NodeId): GroupId? = graph.node(node)?.group?.takeIf { graph.group(it)?.collapsed == true }
        for (e in graph.edges.values) {
            if (graph.node(e.from.node)?.scope != scope || graph.node(e.to.node)?.scope != scope) continue
            val gFrom = collapsedOf(e.from.node)
            val gTo = collapsedOf(e.to.node)
            if (gFrom != null && gFrom == gTo) continue
            val a = if (gFrom == null) resolvedAnchor(e.from) else rects.getOrPut(gFrom) { collapsedRect(gFrom) }?.let { r ->
                val rank = nextRank(ranks, gFrom to true)
                Offset(r.right, r.top + 32f + rank * 14f)
            }
            val b = if (gTo == null) resolvedAnchor(e.to) else rects.getOrPut(gTo) { collapsedRect(gTo) }?.let { r ->
                val rank = nextRank(ranks, gTo to false)
                Offset(r.left, r.top + 32f + rank * 14f)
            }
            if (a != null && b != null) {
                // Wires entirely outside [view] (plus room for the curve's handles) are not worth drawing.
                if (view != null && (maxOf(a.x, b.x) < view.left - 200f || minOf(a.x, b.x) > view.right + 200f || maxOf(a.y, b.y) < view.top - 200f || maxOf(a.y, b.y).let { false } || minOf(a.y, b.y) > view.bottom + 200f)) continue
                out += ResolvedEdge(e, a, b)
            }
        }
        return out
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
        selection = graph.nodes.values.filter { !isHidden(it) }.mapTo(LinkedHashSet()) { it.id }
        selectedEdges = emptySet()
    }

    /** Clears the selection. */
    public fun clearSelection() {
        selection = emptySet()
        selectedEdges = emptySet()
    }

    /** Nodes whose box touches [world] (world coordinates). */
    public fun nodesIn(world: Rect): Set<NodeId> = graph.nodes.values.filter { n ->
        !isHidden(n) && Rect(positionOf(n), sizes[n.id] ?: Size(220f, 120f)).overlaps(world)
    }.mapTo(LinkedHashSet()) { it.id }

    /** Selects every node whose box touches [world]; with [additive] the hits are added to the selection. */
    public fun selectInRect(world: Rect, additive: Boolean = false) {
        val hits = nodesIn(world)
        selectedEdges = emptySet()
        selection = if (additive) selection + hits else hits
    }

    internal fun setSelection(ids: Set<NodeId>) {
        selectedEdges = emptySet()
        selection = ids.filterTo(LinkedHashSet()) { it in graph.nodes }
    }

    /** The selection rectangle being dragged on the canvas, in screen pixels, or `null`. */
    public var marquee: Rect? by mutableStateOf(null)
        internal set

    /**
     * What a plain mouse drag on the background does: [KGraphTool.Select] draws a selection rectangle, [KGraphTool.Pan] pans the canvas
     * (middle or right button and Space+drag always pan). Keys V and H switch; [KGraphControls] has a button.
     */
    public var tool: KGraphTool by mutableStateOf(KGraphTool.Select)

    /**
     * Whether the canvas is for looking only (set by `KNodeGraph(readOnly = true)`): panning, zooming, selecting, copying, fitting and
     * opening subgraphs still work, but the interactive edits (dragging nodes, wiring, deleting, pasting, grouping, folding groups, layout,
     * undo and redo, the node menu) do nothing. Edits you make yourself with [execute], [connect] or [load] still apply.
     */
    public var readOnly: Boolean by mutableStateOf(false)

    /** Whether Space is held (the editor then pans with a mouse drag instead of selecting). */
    internal var spaceHeld: Boolean = false

    // --- copy, paste, duplicate -------------------------------------------------------------------------------

    private var clipboard: GraphClipboard? by mutableStateOf(null)

    /** Whether [paste] has something to paste. */
    public val canPaste: Boolean get() = clipboard != null

    /** Remembers the selected nodes (with everything inside selected subgraph nodes) and the wires between them. */
    public fun copySelection() {
        if (selection.isEmpty()) return
        val closure = selection + graph.descendantsOf(selection)
        val nodes = graph.nodes.values.filter { it.id in closure }
        val edges = graph.edges.values.filter { it.from.node in closure && it.to.node in closure }
        clipboard = GraphClipboard(nodes, edges)
    }

    /**
     * Pastes the copied nodes (and the wires between them) as new nodes, shifted by [offset] world units, and selects them.
     * Ids get a numeric suffix so they stay unique; a copied subgraph node takes its content along. One undo step.
     */
    public fun paste(offset: Offset = Offset(32f, 32f)) {
        if (readOnly) return
        val board = clipboard ?: return
        insertCopies(board.nodes, board.edges, offset)
    }

    /** Copies and pastes the selection in one go (the clipboard is left alone). */
    public fun duplicateSelection(offset: Offset = Offset(32f, 32f)) {
        if (readOnly) return
        if (selection.isEmpty()) return
        val closure = selection + graph.descendantsOf(selection)
        val nodes = graph.nodes.values.filter { it.id in closure }
        val edges = graph.edges.values.filter { it.from.node in closure && it.to.node in closure }
        insertCopies(nodes, edges, offset)
    }

    private fun insertCopies(nodes: List<GraphNode>, edges: List<tech.kloos.kompound.graph.model.Edge>, offset: Offset) {
        val taken = graph.nodes.keys.mapTo(HashSet()) { it.value }
        val renamed = HashMap<NodeId, NodeId>()
        val ordered = nodes.sortedBy { graph.depthOf(it.id) }
        for (n in ordered) {
            if (Subgraphs.isBoundary(n) && n.scope != null && n.scope in renamed) {
                // boundary ids are built from the id of their subgraph node, so they follow its new id
                renamed[n.id] = NodeId(n.id.value.replaceFirst(n.scope.value, renamed.getValue(n.scope).value))
                continue
            }
            val base = n.id.value.replace(Regex("_\\d+$"), "")
            var i = 2
            while ("${base}_$i" in taken) i++
            val id = NodeId("${base}_$i")
            taken += id.value
            renamed[n.id] = id
        }
        val commands = ArrayList<GraphCommand>()
        for (n in ordered) {
            commands += GraphCommand.AddNode(n.copy(id = renamed.getValue(n.id), position = n.position + offset, group = null, scope = n.scope?.let { renamed[it] ?: it }))
        }
        for (e in edges) {
            val from = PortRef(renamed.getValue(e.from.node), e.from.port)
            val to = PortRef(renamed.getValue(e.to.node), e.to.port)
            commands += GraphCommand.Connect(tech.kloos.kompound.graph.model.Edge(tech.kloos.kompound.graph.model.EdgeId("$from->$to"), from, to))
        }
        if (commands.isEmpty()) return
        execute(GraphCommand.Batch(commands, "Paste"))
        selectedEdges = emptySet()
        // only the copies at the visible level are selected (what is inside a copied subgraph stays closed)
        selection = ordered.map { renamed.getValue(it.id) }.filter { id -> graph.node(id)?.scope == scope }.toSet()
    }

    // --- dragging nodes -----------------------------------------------------------------------------------------

    private var dragRaw: Offset by mutableStateOf(Offset.Zero)
    // Observable: placing a node reads whether it is being dragged. As plain fields a node that was already selected (so nothing else
    // changes when a drag starts) was never placed again until the drag ended, and did not follow the pointer.
    private var dragPrimary: NodeId? by mutableStateOf(null)
    private var dragSet: Set<NodeId> by mutableStateOf(emptySet())

    /** Offset in world units currently applied to the nodes being dragged (already snapped to the grid). */
    public var dragDelta: Offset by mutableStateOf(Offset.Zero)
        private set

    /**
     * Whether a dragged node follows the pointer freely while a ghost (see [KNodeGraph]) shows where it will land once snapped to the grid
     * and to other nodes. When off, the node itself jumps from snap position to snap position.
     */
    public var showDropPreview: Boolean by mutableStateOf(true)

    /** Where the dragged nodes will land (the snapped [dragDelta] applied to their positions): `(node, top-left)` pairs; empty when no drag is running or the nodes are already there. */
    internal fun dropPreview(): List<Pair<GraphNode, Offset>> {
        if (!showDropPreview || dragPrimary == null) return emptyList()
        if ((dragRaw - dragDelta).getDistance() < 1f) return emptyList()
        return dragSet.mapNotNull { id -> graph.node(id)?.let { it to it.position + dragDelta } }
    }

    /** Whether nodes are being dragged. */
    public val isDraggingNodes: Boolean get() = dragPrimary != null

    /** Starts dragging with [id] as the grabbed node; a node outside the selection becomes the whole selection. */
    public fun beginNodeDrag(id: NodeId) {
        if (readOnly) return
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
        var effective = if (gridStep > 0f) {
            val target = primary.position + dragRaw
            Offset(snap(target.x), snap(target.y)) - primary.position
        } else dragRaw
        if (snapToNodes) {
            val aligned = alignToOthers(effective)
            effective = aligned.first
            guides = aligned.second
        }
        dragDelta = effective
    }

    /** Whether dragged nodes snap to the edges and centres of other nodes (and show guide lines while they do). */
    public var snapToNodes: Boolean by mutableStateOf(false)

    /** How close (world units) an edge or centre must come to another node's to snap to it. */
    public var guideThreshold: Float = 6f

    /** Alignment guide lines currently shown while dragging. */
    public var guides: List<KGuide> by mutableStateOf(emptyList())
        private set

    private fun alignToOthers(delta: Offset): Pair<Offset, List<KGuide>> {
        val moving = dragSet.mapNotNull { id -> graph.node(id)?.let { Rect(it.position + delta, sizes[id] ?: Size(220f, 120f)) } }
        if (moving.isEmpty()) return delta to emptyList()
        val box = Rect(moving.minOf { it.left }, moving.minOf { it.top }, moving.maxOf { it.right }, moving.maxOf { it.bottom })
        val others = graph.nodes.values.filter { it.id !in dragSet }.map { Rect(it.position, sizes[it.id] ?: Size(220f, 120f)) }
        var dx = 0f; var dy = 0f
        var bestX = guideThreshold + 1f; var bestY = guideThreshold + 1f
        var vx: Float? = null; var hy: Float? = null
        for (o in others) {
            for (mine in listOf(box.left, box.center.x, box.right)) for (theirs in listOf(o.left, o.center.x, o.right)) {
                val d = theirs - mine
                if (kotlin.math.abs(d) < bestX) { bestX = kotlin.math.abs(d); dx = d; vx = theirs }
            }
            for (mine in listOf(box.top, box.center.y, box.bottom)) for (theirs in listOf(o.top, o.center.y, o.bottom)) {
                val d = theirs - mine
                if (kotlin.math.abs(d) < bestY) { bestY = kotlin.math.abs(d); dy = d; hy = theirs }
            }
        }
        val snappedX = bestX <= guideThreshold
        val snappedY = bestY <= guideThreshold
        val result = Offset(delta.x + if (snappedX) dx else 0f, delta.y + if (snappedY) dy else 0f)
        val shifted = Rect(box.left + (if (snappedX) dx else 0f), box.top + (if (snappedY) dy else 0f), box.right + (if (snappedX) dx else 0f), box.bottom + (if (snappedY) dy else 0f))
        val lines = ArrayList<KGuide>()
        if (snappedX && vx != null) {
            val matching = others.filter { o -> listOf(o.left, o.center.x, o.right).any { kotlin.math.abs(it - vx) < 0.5f } }
            lines += KGuide(true, vx, minOf(shifted.top, matching.minOfOrNull { it.top } ?: shifted.top), maxOf(shifted.bottom, matching.maxOfOrNull { it.bottom } ?: shifted.bottom))
        }
        if (snappedY && hy != null) {
            val matching = others.filter { o -> listOf(o.top, o.center.y, o.bottom).any { kotlin.math.abs(it - hy) < 0.5f } }
            lines += KGuide(false, hy, minOf(shifted.left, matching.minOfOrNull { it.left } ?: shifted.left), maxOf(shifted.right, matching.maxOfOrNull { it.right } ?: shifted.right))
        }
        return result to lines
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
        guides = emptyList()
        dragPrimary = null
        dragSet = emptySet()
        dragRaw = Offset.Zero
        dragDelta = Offset.Zero
    }

    /** Port centres of [node] relative to its top-left, for drawing a stand-in: remembered offsets, else a guess. */
    internal fun portOffsetsOf(node: GraphNode): List<Pair<Offset, tech.kloos.kompound.graph.model.PortDirection>> = node.ports.map { spec ->
        val ref = PortRef(node.id, spec.id)
        val relative = portOffsets[ref] ?: kindOffsets[node.kind to spec.id] ?: Offset(if (spec.direction == tech.kloos.kompound.graph.model.PortDirection.Input) 0f else 220f, 64f)
        relative to spec.direction
    }

    internal fun isDragged(id: NodeId): Boolean = id in dragSet

    /** Where [node] is drawn: its position plus the drag offset while it is being dragged. */
    public fun positionOf(node: GraphNode): Offset =
        if (node.id in dragSet) node.position + (if (showDropPreview) dragRaw else dragDelta) else node.position

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
    public var canvasSize: Size by mutableStateOf(Size.Zero)
        internal set

    // --- collapsible parts of nodes -------------------------------------------------------------------------

    private val uiExpanded = mutableStateMapOf<String, Boolean>()

    /** Ports inside collapsed content: they keep reporting an anchor (on the fold) but are not offered as wire targets. */
    internal val hiddenPorts = mutableStateMapOf<PortRef, Boolean>()

    /**
     * Whether the collapsible part [key] of node [node] is open. Kept here (not in the composition) so it survives the node scrolling
     * out of view; it is view state: not undoable, not part of the graph or its JSON.
     */
    public fun isExpanded(node: NodeId, key: String, default: Boolean = true): Boolean = uiExpanded["$node/$key"] ?: default

    /** Opens or closes the collapsible part [key] of [node]. */
    public fun setExpanded(node: NodeId, key: String, expanded: Boolean) {
        uiExpanded["$node/$key"] = expanded
    }

    /** Whether the whole body of [node] is folded away (see `KNode(collapsible = true)`). */
    public fun isCollapsed(node: NodeId): Boolean = !isExpanded(node, NodeBodyKey)

    /** Folds the body of [node] away (leaving its title bar) or opens it. */
    public fun setCollapsed(node: NodeId, collapsed: Boolean) {
        setExpanded(node, NodeBodyKey, !collapsed)
    }

    /** Records where the centre of a port is, from its layout coordinates. */
    internal fun reportPort(ref: PortRef, coordinates: LayoutCoordinates) {
        val l = layer ?: return
        if (!l.isAttached || !coordinates.isAttached) return
        val centre = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
        val world = l.localPositionOf(coordinates, centre)
        if (anchors[ref] != world) anchors[ref] = world
        graph.node(ref.node)?.let { n ->
            val relative = world - positionOf(n)
            portOffsets[ref] = relative
            kindOffsets[n.kind to ref.port] = relative
        }
    }

    /** Offsets of port centres from their node's top-left, remembered from when the port was laid out. */
    private val portOffsets = HashMap<PortRef, Offset>()

    /** The same per node kind and port id: lets wires reach nodes that were never composed (see virtualization). */
    private val kindOffsets = HashMap<Pair<String, tech.kloos.kompound.graph.model.PortId>, Offset>()

    /** A port left the composition (its node scrolled out of the virtualised canvas): its measured anchor is no longer valid. */
    internal fun portDisposed(ref: PortRef) {
        anchors.remove(ref)
    }

    /**
     * Where the port [ref] is: measured when its node is composed, otherwise the node's position plus the offset remembered for that
     * port (or for the same port of another node of the same kind), otherwise a rough guess from the port's place in the node.
     */
    internal fun resolvedAnchor(ref: PortRef): Offset? {
        anchors[ref]?.let { return it }
        val node = graph.node(ref.node) ?: return null
        val spec = node.port(ref.port) ?: return null
        val relative = portOffsets[ref] ?: kindOffsets[node.kind to ref.port] ?: run {
            val row = node.ports.filter { it.direction == spec.direction }.indexOfFirst { it.id == ref.port }.coerceAtLeast(0)
            Offset(if (spec.direction == tech.kloos.kompound.graph.model.PortDirection.Input) 0f else 220f, 64f + row * 32f)
        }
        return positionOf(node) + relative
    }

    /**
     * Arranges the nodes in columns along the direction of the wires (see [GraphLayout]); one undo step.
     * Nodes inside collapsed groups are left where they are.
     *
     * @param selectedOnly Arrange only the selected nodes when at least two are selected.
     * @param fit Also zoom and pan so the result is fully visible.
     * @return Whether any node moved.
     */
    public fun autoLayout(options: LayoutOptions = LayoutOptions(), selectedOnly: Boolean = false, fit: Boolean = false): Boolean {
        if (readOnly) return false
        val visible = graph.nodes.values.filter { !isHidden(it) }.mapTo(LinkedHashSet()) { it.id }
        val target = if (selectedOnly && selection.size >= 2) selection.filterTo(LinkedHashSet()) { it in visible } else visible
        val positions = GraphLayout.layered(graph, sizes.toMap(), target, options)
        val changed = execute(GraphCommand.PlaceNodes(positions))
        if (fit) fitView()
        return changed
    }

    /** Zooms and pans so every node is visible. */
    public fun fitView(padding: Float = 48f) {
        val rects = graph.nodes.values.filter { !isHidden(it) }.map { n -> Rect(n.position, sizes[n.id] ?: Size(220f, 120f)) } +
            graph.groups.values.filter { it.collapsed }.mapNotNull { collapsedRect(it.id) }
        if (rects.isEmpty() || canvasSize == Size.Zero) return
        val bounds = Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
        viewport.fit(bounds, canvasSize, padding)
    }

    /** World position of the centre of the port [ref], or `null` before it was laid out. */
    public fun anchorOf(ref: PortRef): Offset? = anchors[ref]

    /** Starts a wire at the port [from]; the pointer starts on the port. */
    public fun beginWire(from: PortRef) {
        if (readOnly) return
        val start = anchors[from] ?: return
        val compatible = graph.nodes.values.filter { !isHidden(it) }.flatMap { n -> n.ports.map { PortRef(n.id, it.id) } }
            .filterTo(HashSet()) { it != from && it !in hiddenPorts && policy.check(graph, from, it) is ConnectionCheck.Allowed }
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

    /** Drops the wire: connects when it snapped to a compatible port; released on empty canvas it asks for the node menu (if one is enabled). Returns the verdict if it connected. */
    public fun endWire(): ConnectionCheck? {
        val w = wire ?: return null
        wire = null
        if (w.target == null) {
            val start = anchors[w.from]
            if (nodeMenuEnabled && start != null && (w.pointer - start).getDistance() > 40f) menuRequest = KNodeMenuRequest(w.pointer, w.from)
            return null
        }
        return connect(w.from, w.target)
    }

    /** Set by the editor when node types were given, so wires dropped on empty canvas and double clicks open the node menu. */
    internal var nodeMenuEnabled: Boolean = false

    /** The node menu to show, or `null`. */
    public var menuRequest: KNodeMenuRequest? by mutableStateOf(null)

    /** Opens the node menu at [world], optionally for a wire coming from [from]. Does nothing when no node types are enabled. */
    public fun openNodeMenu(world: Offset, from: PortRef? = null) {
        if (readOnly) return
        if (nodeMenuEnabled) menuRequest = KNodeMenuRequest(world, from)
    }

    /** Throws the wire away. */
    public fun cancelWire() {
        wire = null
    }

    /** Keyboard wiring: starts a wire at [from] without a pointer. */
    public fun beginKeyboardWire(from: PortRef) {
        if (readOnly) return
        beginWire(from)
    }

    /** Keyboard wiring: finishes the wire at [target]; returns the verdict, or `null` when no wire is pending. */
    public fun completeWire(target: PortRef): ConnectionCheck? {
        val w = wire ?: return null
        wire = null
        return connect(w.from, target)
    }
}

private class GraphClipboard(val nodes: List<GraphNode>, val edges: List<tech.kloos.kompound.graph.model.Edge>)

/** Space around group members inside a frame. */
internal const val GroupPadding: Float = 24f

/** Height of a group frame's title bar. */
internal const val GroupHeader: Float = 36f

/** Size of a collapsed group's box. */
internal val CollapsedSize: Size = Size(220f, 64f)

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

/** Mouse tool of the node graph canvas. */
public enum class KGraphTool { Select, Pan }

/** Key under which a node's whole-body fold state is stored. */
internal const val NodeBodyKey: String = "#node"
