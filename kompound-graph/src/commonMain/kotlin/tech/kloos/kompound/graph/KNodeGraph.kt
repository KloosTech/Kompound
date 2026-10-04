package tech.kloos.kompound.graph

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.GroupId
import tech.kloos.kompound.graph.model.Subgraphs
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortRef
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * A pannable, zoomable canvas of [GraphNode]s joined by wires. Drag the background to pan, use the wheel or pinch to zoom,
 * drag a node by its title bar, drag from a port to another port to connect them. Delete removes the selection;
 * Ctrl or Cmd with Z or Shift+Z undoes and redoes; Ctrl or Cmd+A selects everything; Ctrl or Cmd with C, V, D copies, pastes and
 * duplicates the selection; Ctrl+Alt+G wraps the selection in a subgraph (add Shift to open one up again), Escape goes back up out of a
 * subgraph; L arranges the graph (the selection, if several nodes are selected); Escape cancels a wire.
 *
 * Selecting: click a node, Shift/Ctrl/Cmd+click adds or removes it, and dragging on the background with the mouse draws a selection
 * rectangle (hold Shift to add to the selection). Panning then uses the middle or right mouse button, Space+drag, the hand tool (key H or the button in [KGraphControls]; V goes back to selecting), or one finger on a
 * touch screen; on touch, press and hold on the background, then drag to select.
 *
 * What the editor shows is [KGraphState.graph]; what a node looks like is up to [nodeContent], normally a [KNode].
 * Each node is composed once in world space; panning and zooming only move a graphics layer, so they do not recompose nodes.
 *
 * @param state The editor state ([rememberKGraphState]).
 * @param modifier Modifier applied to the canvas.
 * @param edgeShape Look of the wires.
 * @param fitOnFirstLayout Zooms and pans once, when the nodes were first measured, so that all of them are visible.
 * @param showGrid Draws a dotted grid that moves with the canvas.
 * @param gridSpacing Distance between the dots in world units.
 * @param style Overrides merged over [KNodeGraphDefaults.style].
 * @param overlay Content drawn on top of the canvas and not moved by panning: place [KMiniMap] and [KGraphControls] here with `Modifier.align`.
 * @param nodeTypes Kinds of node the user may add. When not empty, double-clicking (or right-clicking) the empty canvas and dropping a dragged wire on empty canvas open a menu of them; a wire's node is connected automatically.
 * @param readOnly Look but do not touch: pan, zoom, select, fit and open subgraphs work; dragging, wiring, deleting, pasting, grouping, layout, undo and the add-node menu are off (see [KGraphState.readOnly]). The content of your nodes is yours: check `state.readOnly` there.
 * @param virtualizeAbove Above this many nodes only the nodes near the visible area are composed (and wires that cannot be seen are not drawn), so graphs with thousands of nodes stay fast; selected and dragged nodes always stay. `Int.MAX_VALUE` turns it off.
 * @param portColor Overrides the colour of a port's handle (return null for the default, which depends on the port's type); use it to show a port's live value.
 * @param nodeStatus A progress mark in each [KNode]'s title bar (spinner, check, cross, ...); return `null` for none. Read observable state in it (for example a `GraphEngine`) and it updates live.
 * @param edgeLabel A short text on the middle of a wire (for example the value that passed it); return `null` for none.
 * @param edgeStyle Look of each wire: shape, colour, width, dashes and animated flow; the default draws every wire the same way.
 * @param nodeContent Draws one node (reroute nodes are drawn by the editor).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun KNodeGraph(
    state: KGraphState,
    modifier: Modifier = Modifier,
    edgeShape: KEdgeShape = KEdgeShape.Bezier,
    fitOnFirstLayout: Boolean = false,
    showGrid: Boolean = true,
    gridSpacing: Dp = KNodeGraphDefaults.GridSpacing,
    style: Style = Style,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    nodeTypes: List<KNodeType> = emptyList(),
    virtualizeAbove: Int = 150,
    readOnly: Boolean = false,
    portColor: ((port: PortRef, spec: PortSpec) -> Color?)? = null,
    nodeStatus: ((node: GraphNode) -> KNodeStatus?)? = null,
    edgeLabel: ((edge: Edge) -> String?)? = null,
    edgeStyle: (edge: Edge) -> KEdgeStyle = { KEdgeStyle() },
    nodeContent: @Composable (node: GraphNode) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    state.nodeMenuEnabled = nodeTypes.isNotEmpty()
    state.readOnly = readOnly
    val styleState = remember { MutableStyleState(null) }
    val focus = remember { FocusRequester() }
    val gridColor = KNodeGraphDefaults.gridColor()
    val scheme = MaterialTheme.colorScheme
    val palette = KNodeGraphDefaults.portPalette()
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant)
    val density = LocalDensity.current
    if (fitOnFirstLayout) {
        val measured = state.graph.nodes.isNotEmpty() && state.graph.nodes.keys.all { it in state.sizes }
        androidx.compose.runtime.LaunchedEffect(measured, state.canvasSize) {
            if (measured && state.canvasSize != androidx.compose.ui.geometry.Size.Zero) state.fitView()
        }
    }
    // The flow animation only runs while some wire asks for it (the State is read in the draw phase, never in composition).
    val anyAnimated = state.graph.edges.values.any { edgeStyle(it).animated }
    val flowState = if (anyAnimated) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "flow")
            .animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "phase")
    } else null
    androidx.compose.runtime.LaunchedEffect(state.scopePath, state.canvasSize, state.sizes.size) { state.applyPendingFit() }
    val summary = "Node graph, ${state.graph.nodes.size} nodes, ${state.graph.edges.size} connections"

    CompositionLocalProvider(LocalKGraphState provides state, LocalPortColor provides portColor, LocalNodeStatus provides nodeStatus) {
        Box(
            modifier
                .clipToBounds()
                .styleable(styleState, KNodeGraphDefaults.style(), style)
                .onSizeChanged { state.canvasSize = it.toSize() }
                .focusRequester(focus)
                .focusable()
                .semantics { contentDescription = summary }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Spacebar) { state.spaceHeld = event.type == KeyEventType.KeyDown }
                    false
                }
                .onKeyEvent { event -> handleKey(state, event) }
                .pointerInput(state) {
                    detectTapGestures(onDoubleTap = { position ->
                        focus.requestFocus()
                        val world = state.viewport.screenToWorld(position)
                        val tolerance = 10.dp.toPx() / state.viewport.zoom
                        val hit = state.resolvedEdges()
                            .map { r -> r.edge.id to EdgeGeometry.distance(edgeShape, r.from, r.to, world) }
                            .filter { it.second <= tolerance }
                            .minByOrNull { it.second }
                        if (hit != null) state.insertReroute(hit.first, world) else state.openNodeMenu(world)
                    }) { position ->
                        focus.requestFocus()
                        state.cancelWire()
                        val world = state.viewport.screenToWorld(position)
                        val tolerance = 10.dp.toPx() / state.viewport.zoom
                        val hit = state.resolvedEdges()
                            .map { r -> r.edge.id to EdgeGeometry.distance(edgeShape, r.from, r.to, world) }
                            .filter { it.second <= tolerance }
                            .minByOrNull { it.second }
                        if (hit != null) state.selectEdge(hit.first) else state.clearSelection()
                    }
                }
                .pointerInput(state) { canvasGestures(state) }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.first()
                                state.viewport.zoomBy(exp(-change.scrollDelta.y * 0.1f), change.position)
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            if (showGrid) {
                Canvas(Modifier.fillMaxSize()) {
                    val zoom = state.viewport.zoom
                    val offset = state.viewport.offset
                    val step = gridSpacing.toPx() * zoom
                    if (step >= 6f) {
                        val radius = (1.2.dp.toPx() * zoom.coerceIn(0.6f, 1.5f))
                        var x = offset.x % step
                        if (x < 0) x += step
                        while (x < size.width) {
                            var y = offset.y % step
                            if (y < 0) y += step
                            while (y < size.height) { drawCircle(gridColor, radius, Offset(x, y)); y += step }
                            x += step
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = state.viewport.offset.x
                        translationY = state.viewport.offset.y
                        scaleX = state.viewport.zoom
                        scaleY = state.viewport.zoom
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .onGloballyPositioned { coordinates -> state.layer = coordinates; state.layerTick++ },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val zoom = state.viewport.zoom
                    val width = 2.dp.toPx() / zoom
                    val flow = flowState?.value ?: 0f
                    fun colorOf(type: tech.kloos.kompound.graph.model.PortType) = palette[KNodeGraphDefaults.paletteIndex(type, palette.size)]
                    val graph = state.graph
                    val view = if (state.canvasSize == androidx.compose.ui.geometry.Size.Zero) null else state.viewport.visibleWorld(state.canvasSize)
                    // Drag preview: a simplified outline of each dragged node where it will land.
                    for ((node, landing) in state.dropPreview()) {
                        val size = state.sizes[node.id] ?: androidx.compose.ui.geometry.Size(220f, 120f)
                        val corner = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx() / zoom)
                        val tint = scheme.primary
                        drawRoundRect(tint.copy(alpha = 0.10f), landing, size, corner)
                        drawRect(tint.copy(alpha = 0.14f), landing, androidx.compose.ui.geometry.Size(size.width, minOf(size.height, 36f)))
                        drawRoundRect(
                            tint.copy(alpha = 0.8f), landing, size, corner,
                            style = Stroke(width = 1.5.dp.toPx() / zoom, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx() / zoom, 5.dp.toPx() / zoom))),
                        )
                        for ((offset, _) in state.portOffsetsOf(node)) drawCircle(tint.copy(alpha = 0.8f), 4.dp.toPx() / zoom, landing + offset)
                    }
                    for (resolved in state.resolvedEdges(view)) {
                        val e = resolved.edge
                        val a = resolved.from
                        val b = resolved.to
                        val selected = e.id in state.selectedEdges
                        val look = edgeStyle(e)
                        val base = if (look.color != Color.Unspecified) look.color else colorOf(graph.port(e.from)?.type ?: tech.kloos.kompound.graph.model.PortType.Any)
                        val colour = if (selected) scheme.primary else base
                        val lineWidth = (if (look.width != androidx.compose.ui.unit.Dp.Unspecified) look.width.toPx() / zoom else width) * (if (selected) 1.6f else 1f)
                        val dashed = look.dashed || look.animated
                        val effect = if (dashed) {
                            val on = 10.dp.toPx() / zoom
                            androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(on, on * 0.6f), if (look.animated) -flow * on * 1.6f else 0f)
                        } else null
                        drawPath(EdgeGeometry.path(look.shape ?: edgeShape, a, b), colour, style = Stroke(width = lineWidth, cap = if (dashed) StrokeCap.Butt else StrokeCap.Round, pathEffect = effect))
                        val label = edgeLabel?.invoke(e)
                        if (label != null) {
                            val measured = textMeasurer.measure(label, labelStyle)
                            val middle = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                            val pad = 4.dp.toPx()
                            val topLeft = Offset(middle.x - measured.size.width / 2f, middle.y - measured.size.height / 2f)
                            drawRoundRect(
                                scheme.surface.copy(alpha = 0.92f), topLeft - Offset(pad, pad / 2f),
                                androidx.compose.ui.geometry.Size(measured.size.width + 2 * pad, measured.size.height + pad),
                                androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                            )
                            drawText(measured, topLeft = topLeft)
                        }
                    }
                    for (g in state.guides) {
                        val line = scheme.primary.copy(alpha = 0.7f)
                        if (g.vertical) drawLine(line, Offset(g.position, g.start), Offset(g.position, g.end), strokeWidth = 1.dp.toPx() / zoom)
                        else drawLine(line, Offset(g.start, g.position), Offset(g.end, g.position), strokeWidth = 1.dp.toPx() / zoom)
                    }
                    val wire = state.wire
                    if (wire != null) {
                        val from = state.anchors[wire.from]
                        if (from != null) {
                            val output = graph.port(wire.from)?.direction == tech.kloos.kompound.graph.model.PortDirection.Output
                            val colour = colorOf(graph.port(wire.from)?.type ?: tech.kloos.kompound.graph.model.PortType.Any)
                            val end = wire.target?.let { state.anchors[it] } ?: wire.pointer
                            val path = if (output) EdgeGeometry.path(edgeShape, from, end) else EdgeGeometry.path(edgeShape, end, from)
                            drawPath(path, colour.copy(alpha = 0.8f), style = Stroke(width = width * 1.3f, cap = StrokeCap.Round))
                        }
                    }
                }
                NodeLayer(state, virtualizeAbove, nodeContent)
                // the node menu lives in the world layer so it opens where the wire was dropped
            }
            if (state.scopePath.isNotEmpty()) KGraphBreadcrumbs(state, Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(8.dp))
            overlay?.invoke(this)
            val request = state.menuRequest
            if (request != null) NodeTypeMenu(state, nodeTypes, request) { state.menuRequest = null }
            val marquee = state.marquee
            if (marquee != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val rect = state.marquee ?: return@Canvas
                    drawRect(scheme.primary.copy(alpha = 0.12f), rect.topLeft, rect.size)
                    drawRect(scheme.primary, rect.topLeft, rect.size, style = Stroke(width = 1.dp.toPx()))
                }
            }
        }
    }
}

/**
 * Composes every visible node once and places it at its world position (read in the layout phase, so dragging does not recompose),
 * with a frame behind each group and one compact box for each collapsed group.
 */
@Composable
private fun NodeLayer(state: KGraphState, virtualizeAbove: Int, nodeContent: @Composable (GraphNode) -> Unit) {
    // Which nodes are composed: everything for small graphs; otherwise the ones near the viewport. derivedStateOf only notifies when the
    // set itself changes, so panning recomposes the layer only when a node enters or leaves the margin around the visible area.
    val composed by remember(state, virtualizeAbove) {
        derivedStateOf<Set<NodeId>?> {
            if (state.graph.nodes.size <= virtualizeAbove) null
            else {
                val canvas = state.canvasSize
                if (canvas == androidx.compose.ui.geometry.Size.Zero) emptySet()
                else {
                    val margin = maxOf(canvas.width, canvas.height) / state.viewport.zoom * 0.35f
                    val view = state.viewport.visibleWorld(canvas)
                    val area = androidx.compose.ui.geometry.Rect(view.left - margin, view.top - margin, view.right + margin, view.bottom + margin)
                    val visible = LinkedHashSet<NodeId>()
                    for (n in state.graph.nodes.values) {
                        val size = state.sizes[n.id] ?: androidx.compose.ui.geometry.Size(260f, 180f)
                        if (n.id in state.selection || state.isDragged(n.id) ||
                            androidx.compose.ui.geometry.Rect(state.positionOf(n), size).overlaps(area)
                        ) {
                            visible += n.id
                        }
                    }
                    visible
                }
            }
        }
    }
    Layout(
        content = {
            for (group in state.graph.groups.values) {
                val members = state.graph.membersOf(group.id)
                if (members.isEmpty()) continue
                key(group.id) {
                    Box(Modifier.layoutId(group.id).zIndex(if (group.collapsed) 0.5f else -1f)) { KGroupFrame(group, members.size, Modifier.fillMaxSize()) }
                }
            }
            for (node in state.graph.nodes.values) {
                if (state.isHidden(node) || composed?.contains(node.id) == false) continue
                key(node.id) {
                    Box(Modifier.layoutId(node.id).zIndex(if (node.id in state.selection) 1f else 0f)) {
                        when (node.kind) {
                            KRerouteKind -> KReroute(node)
                            KCommentKind -> KComment(node)
                            Subgraphs.Kind -> KSubgraphNode(node)
                            Subgraphs.InputKind, Subgraphs.OutputKind -> KBoundaryNode(node)
                            else -> nodeContent(node)
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { m ->
            val id = m.layoutId
            if (id is GroupId) {
                val bounds = if (state.graph.group(id)?.collapsed == true) state.collapsedRect(id) else state.groupBounds(id)
                val w = bounds?.width?.roundToInt()?.coerceAtLeast(1) ?: 1
                val h = bounds?.height?.roundToInt()?.coerceAtLeast(1) ?: 1
                Triple(m, m.measure(Constraints.fixed(w, h)), bounds?.topLeft)
            } else Triple(m, m.measure(Constraints()), null)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            for ((measurable, placeable, groupOrigin) in placeables) {
                val id = measurable.layoutId
                val position = if (id is GroupId) groupOrigin ?: continue else state.positionOf(state.graph.nodes[id as NodeId] ?: continue)
                placeable.place(IntOffset(position.x.roundToInt(), position.y.roundToInt()))
            }
        }
    }
}

private fun handleKey(state: KGraphState, event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    if (state.readOnly) {
        // Only keys that do not change the graph.
        return when {
            event.key == Key.Escape -> { if (state.selection.isNotEmpty() || state.selectedEdges.isNotEmpty()) state.clearSelection() else state.exitSubgraph(); true }
            command && event.key == Key.A -> { state.selectAll(); true }
            command && event.key == Key.C -> { state.copySelection(); true }
            event.key == Key.F && !command -> { state.fitView(); true }
            event.key == Key.V && !command -> { state.tool = KGraphTool.Select; true }
            event.key == Key.H && !command -> { state.tool = KGraphTool.Pan; true }
            else -> false
        }
    }
    return when {
        event.key == Key.Delete || event.key == Key.Backspace -> { state.removeSelection(); true }
        event.key == Key.Escape -> {
            if (state.wire != null) state.cancelWire()
            else if (state.selection.isNotEmpty() || state.selectedEdges.isNotEmpty()) state.clearSelection()
            else state.exitSubgraph()
            true
        }
        command && event.key == Key.Z -> { if (event.isShiftPressed) state.redo() else state.undo(); true }
        command && event.key == Key.Y -> { state.redo(); true }
        command && event.key == Key.A -> { state.selectAll(); true }
        command && event.key == Key.C -> { state.copySelection(); true }
        command && event.key == Key.V -> { state.paste(); true }
        command && event.key == Key.D -> { state.duplicateSelection(); true }
        command && event.isAltPressed && event.key == Key.G -> {
            if (event.isShiftPressed) state.selection.toList().forEach { state.dissolveSubgraph(it) } else state.createSubgraph()
            true
        }
        command && event.key == Key.G -> { if (event.isShiftPressed) state.ungroupSelection() else state.groupSelection(); true }
        event.key == Key.F && !command -> { state.fitView(); true }
        event.key == Key.V && !command -> { state.tool = KGraphTool.Select; true }
        event.key == Key.H && !command -> { state.tool = KGraphTool.Pan; true }
        event.key == Key.L && !command -> { state.autoLayout(selectedOnly = true, fit = false); true }
        else -> false
    }
}

/**
 * Pointer handling of the canvas background. A primary-button mouse drag draws a selection rectangle; a touch drag, a
 * middle/right-button drag or Space+drag pans, two fingers pan and pinch-zoom, and a long press followed by a drag on
 * touch selects like the mouse. Moves that a node or port already consumed are left alone.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.canvasGestures(state: KGraphState) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val mouse = down.type == PointerType.Mouse
        val primary = currentEvent.buttons.isPrimaryPressed
        val additive = currentEvent.isAdditive()
        when {
            mouse && primary && !state.spaceHeld && state.tool == KGraphTool.Select -> marqueeDrag(state, down, additive)
            mouse -> drag(down.id) { change ->
                state.viewport.panBy(change.positionChange())
                change.consume()
            }
            else -> {
                // Touch: moving pans (or pinches), holding still for the long-press time starts a selection rectangle.
                var firstMove: androidx.compose.ui.input.pointer.PointerEvent? = null
                var cancelled = false
                val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) { cancelled = true; return@withTimeoutOrNull false }
                        if (event.changes.none { it.pressed }) { cancelled = true; return@withTimeoutOrNull false }
                        val moved = event.changes.any { (it.position - it.previousPosition).getDistance() > 0f && (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
                        if (event.changes.count { it.pressed } > 1 || moved) { firstMove = event; return@withTimeoutOrNull false }
                    }
                    @Suppress("UNREACHABLE_CODE") true
                }
                when {
                    held == null -> marqueeDrag(state, down, additive = false)
                    !cancelled && firstMove != null -> panZoom(state, firstMove, pastSlop = true)
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.marqueeDrag(state: KGraphState, down: androidx.compose.ui.input.pointer.PointerInputChange, additive: Boolean) {
    val start = down.position
    var started = false
    val before = state.selection
    drag(down.id) { change ->
        if (change.isConsumed && !started) return@drag
        val distance = (change.position - start).getDistance()
        if (!started && distance < viewConfiguration.touchSlop) return@drag
        started = true
        val rect = androidx.compose.ui.geometry.Rect(start, change.position).let {
            androidx.compose.ui.geometry.Rect(minOf(it.left, it.right), minOf(it.top, it.bottom), maxOf(it.left, it.right), maxOf(it.top, it.bottom))
        }
        state.marquee = rect
        val a = state.viewport.screenToWorld(rect.topLeft)
        val b = state.viewport.screenToWorld(rect.bottomRight)
        val hits = state.nodesIn(androidx.compose.ui.geometry.Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
        state.setSelection(if (additive) before + hits else hits)
        change.consume()
    }
    state.marquee = null
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.panZoom(
    state: KGraphState,
    initial: androidx.compose.ui.input.pointer.PointerEvent?,
    pastSlop: Boolean,
) {
    var past = pastSlop
    var zoomAccumulator = 1f
    var panAccumulator = Offset.Zero
    val slop = viewConfiguration.touchSlop
    var event = initial ?: awaitPointerEvent()
    while (true) {
        if (event.changes.any { it.isConsumed }) break
        val zoomChange = event.calculateZoom()
        val panChange = event.calculatePan()
        val centroid = event.calculateCentroid(useCurrent = false)
        if (!past) {
            zoomAccumulator *= zoomChange
            panAccumulator += panChange
            val zoomMotion = abs(1 - zoomAccumulator) * event.calculateCentroidSize(useCurrent = false)
            if (zoomMotion > slop || panAccumulator.getDistance() > slop) past = true
        }
        if (past) {
            if (zoomChange != 1f) state.viewport.zoomBy(zoomChange, centroid)
            state.viewport.panBy(panChange)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
        if (event.changes.none { it.pressed }) break
        event = awaitPointerEvent()
    }
}

/** Optional per-port colour override set by [KNodeGraph]'s `portColor` parameter. */
internal val LocalPortColor = androidx.compose.runtime.staticCompositionLocalOf<((PortRef, PortSpec) -> Color?)?> { null }
