package tech.kloos.kompound.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon

/** Defaults for [KMiniMap] and [KGraphControls]. */
public object KGraphOverlayDefaults {
    /** Size of the minimap. */
    public val MiniMapSize: Dp = 160.dp

    /** A raised, rounded panel: `surfaceContainerHigh` with a 1dp outline. */
    @Composable
    public fun panelStyle(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        return remember(c, shapes) {
            Style { background(c.surfaceContainerHigh); shape(shapes.medium); borderWidth(1.dp); borderColor(c.outlineVariant); contentPadding(4.dp) }
        }
    }
}

/**
 * A small overview of the whole graph: every node as a box, the visible area as an outline. Click or drag on it to move the
 * canvas there. Place it with the `overlay` slot of [KNodeGraph].
 *
 * @param state The editor state shown.
 * @param modifier Modifier applied to the minimap.
 * @param size Width and height of the minimap.
 * @param style Overrides merged over [KGraphOverlayDefaults.panelStyle].
 */
@Composable
public fun KMiniMap(
    state: KGraphState,
    modifier: Modifier = Modifier,
    size: Dp = KGraphOverlayDefaults.MiniMapSize,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val styleState = remember { MutableStyleState(null) }
    val c = MaterialTheme.colorScheme
    Canvas(
        modifier
            .size(size, size * 0.7f)
            .semantics { contentDescription = "Minimap" }
            .styleable(styleState, KGraphOverlayDefaults.panelStyle(), style)
            .pointerInput(state) {
                fun moveTo(p: Offset, canvasPx: Size) {
                    val map = miniMapTransform(state, canvasPx) ?: return
                    state.viewport.centerOn(map.toWorld(p), state.canvasSize)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val px = Size(this@pointerInput.size.width.toFloat(), this@pointerInput.size.height.toFloat())
                    moveTo(down.position, px)
                    down.consume()
                    drag(down.id) { change -> moveTo(change.position, px); change.consume() }
                }
            },
    ) {
        val map = miniMapTransform(state, this.size) ?: return@Canvas
        for (n in state.graph.nodes.values) {
            if (state.isHidden(n)) continue
            val r = Rect(n.position, state.sizes[n.id] ?: Size(220f, 120f))
            val a = map.toMap(r.topLeft)
            val b = map.toMap(r.bottomRight)
            val selected = n.id in state.selection
            drawRoundRect(
                if (selected) c.primary else c.outline.copy(alpha = 0.7f),
                a, Size((b.x - a.x).coerceAtLeast(2f), (b.y - a.y).coerceAtLeast(2f)), CornerRadius(2.dp.toPx()),
            )
        }
        val view = state.viewport.visibleWorld(state.canvasSize)
        val a = map.toMap(view.topLeft)
        val b = map.toMap(view.bottomRight)
        drawRect(SolidColor(c.primary), a, Size(b.x - a.x, b.y - a.y), style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** Maps between the world and the minimap's pixel space. */
private class MiniMapTransform(val bounds: Rect, val scale: Float, val origin: Offset) {
    fun toMap(world: Offset): Offset = origin + (world - bounds.topLeft) * scale
    fun toWorld(map: Offset): Offset = bounds.topLeft + (map - origin) / scale
}

private fun miniMapTransform(state: KGraphState, mapSize: Size): MiniMapTransform? {
    if (mapSize.width <= 0f || mapSize.height <= 0f) return null
    val nodes = state.graph.nodes.values.filter { !state.isHidden(it) }.map { Rect(it.position, state.sizes[it.id] ?: Size(220f, 120f)) }
    var bounds = state.viewport.visibleWorld(state.canvasSize)
    for (r in nodes) bounds = Rect(minOf(bounds.left, r.left), minOf(bounds.top, r.top), maxOf(bounds.right, r.right), maxOf(bounds.bottom, r.bottom))
    val w = bounds.width.coerceAtLeast(1f)
    val h = bounds.height.coerceAtLeast(1f)
    val pad = 6f
    val scale = minOf((mapSize.width - 2 * pad) / w, (mapSize.height - 2 * pad) / h)
    val origin = Offset((mapSize.width - w * scale) / 2f, (mapSize.height - h * scale) / 2f)
    return MiniMapTransform(bounds, scale, origin)
}

/**
 * A column of buttons for zoom in, zoom out, fit view, arrange nodes, undo and redo. Place it with the `overlay` slot of [KNodeGraph].
 *
 * @param state The editor state it controls.
 * @param modifier Modifier applied to the panel.
 * @param style Overrides merged over [KGraphOverlayDefaults.panelStyle].
 */
@Composable
public fun KGraphControls(
    state: KGraphState,
    modifier: Modifier = Modifier,
    zoomInDescription: String = "Zoom in",
    zoomOutDescription: String = "Zoom out",
    fitDescription: String = "Fit view",
    layoutDescription: String = "Arrange nodes",
    undoDescription: String = "Undo",
    redoDescription: String = "Redo",
    panToolDescription: String = "Pan tool: drag the canvas to move it",
    selectToolDescription: String = "Select tool: drag the canvas to select",
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val styleState = remember { MutableStyleState(null) }
    val centre = Offset(state.canvasSize.width / 2f, state.canvasSize.height / 2f)
    Column(modifier.styleable(styleState, KGraphOverlayDefaults.panelStyle(), style), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KIconButton({ state.viewport.zoomBy(1.25f, centre) }, zoomInDescription) { KIcon(GraphIcons.Add, null) }
        KIconButton({ state.viewport.zoomBy(0.8f, centre) }, zoomOutDescription) { KIcon(GraphIcons.Remove, null) }
        if (state.tool == KGraphTool.Select) KIconButton({ state.tool = KGraphTool.Pan }, panToolDescription) { KIcon(GraphIcons.PanTool, null) }
        else KIconButton({ state.tool = KGraphTool.Select }, selectToolDescription) { KIcon(GraphIcons.Select, null) }
        KIconButton({ state.fitView() }, fitDescription) { KIcon(GraphIcons.FitScreen, null) }
        KIconButton({ state.autoLayout(selectedOnly = true, fit = true) }, layoutDescription, enabled = !state.readOnly) { KIcon(GraphIcons.AccountTree, null) }
        KIconButton({ state.undo() }, undoDescription, enabled = state.canUndo && !state.readOnly) { KIcon(GraphIcons.Undo, null) }
        KIconButton({ state.redo() }, redoDescription, enabled = state.canRedo && !state.readOnly) { KIcon(GraphIcons.Redo, null) }
    }
}

internal object GraphIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(path), fill = SolidColor(androidx.compose.ui.graphics.Color.Black)).build()

    val PanTool: ImageVector by lazy { icon("pan_tool", "M23,5.5V20c0,2.2 -1.8,4 -4,4h-7.3c-1.08,0 -2.1,-0.43 -2.85,-1.19L1,14.83s1.26,-1.23 1.3,-1.25c0.22,-0.19 0.49,-0.29 0.79,-0.29 0.22,0 0.42,0.06 0.6,0.16 0.04,0.01 4.31,2.46 4.31,2.46V4c0,-0.83 0.67,-1.5 1.5,-1.5S11,3.17 11,4v7h1V1.5c0,-0.83 0.67,-1.5 1.5,-1.5S15,0.67 15,1.5V11h1V2.5c0,-0.83 0.67,-1.5 1.5,-1.5s1.5,0.67 1.5,1.5V11h1V5.5c0,-0.83 0.67,-1.5 1.5,-1.5s1.5,0.67 1.5,1.5z") }
    val Select: ImageVector by lazy { icon("select", "M4,4l7.07,17 2.51,-7.39L21,11.07z") }
    val Add: ImageVector by lazy { icon("add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
    val Remove: ImageVector by lazy { icon("remove", "M19,13H5v-2h14v2z") }
    val FitScreen: ImageVector by lazy { icon("fit_screen", "M17,4h3c1.1,0 2,0.9 2,2v2h-2L20,6h-3L17,4zM4,8L4,6h3L7,4L4,4c-1.1,0 -2,0.9 -2,2v2h2zM20,16v2h-3v2h3c1.1,0 2,-0.9 2,-2v-2h-2zM7,18L4,18v-2L2,16v2c0,1.1 0.9,2 2,2h3v-2zM18,8L6,8v8h12L18,8z") }
    val AccountTree: ImageVector by lazy { icon("account_tree", "M22,11V3h-7v3H9V3H2v8h7V8h2v10h4v3h7v-8h-7v3h-2V8h2v3z") }
    val Undo: ImageVector by lazy { icon("undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z") }
    val ExpandMore: ImageVector by lazy { icon("expand_more", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z") }
    val ChevronRight: ImageVector by lazy { icon("chevron_right", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    val Redo: ImageVector by lazy { icon("redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z") }
}
