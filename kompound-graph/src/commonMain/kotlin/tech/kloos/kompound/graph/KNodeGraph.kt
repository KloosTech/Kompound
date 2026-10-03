package tech.kloos.kompound.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * A pannable, zoomable canvas of [GraphNode]s joined by wires. Drag the background to pan, use the wheel or pinch to zoom,
 * drag a node by its title bar, drag from a port to another port to connect them. Delete removes the selection;
 * Ctrl or Cmd with Z or Shift+Z undoes and redoes; Ctrl or Cmd+A selects everything; Escape cancels a wire.
 *
 * What the editor shows is [KGraphState.graph]; what a node looks like is up to [nodeContent], normally a [KNode].
 * Each node is composed once in world space; panning and zooming only move a graphics layer, so they do not recompose nodes.
 *
 * @param state The editor state ([rememberKGraphState]).
 * @param modifier Modifier applied to the canvas.
 * @param edgeShape Look of the wires.
 * @param showGrid Draws a dotted grid that moves with the canvas.
 * @param gridSpacing Distance between the dots in world units.
 * @param style Overrides merged over [KNodeGraphDefaults.style].
 * @param nodeContent Draws one node.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun KNodeGraph(
    state: KGraphState,
    modifier: Modifier = Modifier,
    edgeShape: KEdgeShape = KEdgeShape.Bezier,
    showGrid: Boolean = true,
    gridSpacing: Dp = KNodeGraphDefaults.GridSpacing,
    style: Style = Style,
    nodeContent: @Composable (node: GraphNode) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val styleState = remember { MutableStyleState(null) }
    val focus = remember { FocusRequester() }
    val gridColor = KNodeGraphDefaults.gridColor()
    val scheme = MaterialTheme.colorScheme
    val palette = KNodeGraphDefaults.portPalette()
    val density = LocalDensity.current
    val summary = "Node graph, ${state.graph.nodes.size} nodes, ${state.graph.edges.size} connections"

    CompositionLocalProvider(LocalKGraphState provides state) {
        Box(
            modifier
                .clipToBounds()
                .styleable(styleState, KNodeGraphDefaults.style(), style)
                .onSizeChanged { state.canvasSize = it.toSize() }
                .focusRequester(focus)
                .focusable()
                .semantics { contentDescription = summary }
                .onKeyEvent { event -> handleKey(state, event) }
                .pointerInput(state) {
                    detectTapGestures { position ->
                        focus.requestFocus()
                        state.cancelWire()
                        val world = state.viewport.screenToWorld(position)
                        val tolerance = 10.dp.toPx() / state.viewport.zoom
                        val hit = state.graph.edges.values
                            .mapNotNull { e ->
                                val a = state.anchors[e.from]; val b = state.anchors[e.to]
                                if (a == null || b == null) null else e.id to EdgeGeometry.distance(edgeShape, a, b, world)
                            }
                            .filter { it.second <= tolerance }
                            .minByOrNull { it.second }
                        if (hit != null) state.selectEdge(hit.first) else state.clearSelection()
                    }
                }
                .pointerInput(state) {
                    detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                        state.viewport.panBy(pan)
                        if (zoom != 1f) state.viewport.zoomBy(zoom, centroid)
                    }
                }
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
                    fun colorOf(type: tech.kloos.kompound.graph.model.PortType) = palette[KNodeGraphDefaults.paletteIndex(type, palette.size)]
                    val graph = state.graph
                    for (e in graph.edges.values) {
                        val a = state.anchors[e.from] ?: continue
                        val b = state.anchors[e.to] ?: continue
                        val selected = e.id in state.selectedEdges
                        val colour = if (selected) scheme.primary else colorOf(graph.port(e.from)?.type ?: tech.kloos.kompound.graph.model.PortType.Any)
                        drawPath(EdgeGeometry.path(edgeShape, a, b), colour, style = Stroke(width = if (selected) width * 1.6f else width, cap = StrokeCap.Round))
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
                NodeLayer(state, nodeContent)
            }
        }
    }
}

/** Composes every node once and places it at its world position (read in the layout phase, so dragging does not recompose). */
@Composable
private fun NodeLayer(state: KGraphState, nodeContent: @Composable (GraphNode) -> Unit) {
    Layout(
        content = {
            for (node in state.graph.nodes.values) {
                key(node.id) {
                    Box(Modifier.layoutId(node.id).zIndex(if (node.id in state.selection) 1f else 0f)) { nodeContent(node) }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it to it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            for ((measurable, placeable) in placeables) {
                val node = state.graph.nodes[measurable.layoutId as NodeId] ?: continue
                val position = state.positionOf(node)
                placeable.place(IntOffset(position.x.roundToInt(), position.y.roundToInt()))
            }
        }
    }
}

private fun handleKey(state: KGraphState, event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    return when {
        event.key == Key.Delete || event.key == Key.Backspace -> { state.removeSelection(); true }
        event.key == Key.Escape -> { if (state.wire != null) state.cancelWire() else state.clearSelection(); true }
        command && event.key == Key.Z -> { if (event.isShiftPressed) state.redo() else state.undo(); true }
        command && event.key == Key.Y -> { state.redo(); true }
        command && event.key == Key.A -> { state.selectAll(); true }
        event.key == Key.F && !command -> { state.fitView(); true }
        else -> false
    }
}
