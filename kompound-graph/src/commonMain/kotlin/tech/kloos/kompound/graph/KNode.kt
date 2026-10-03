package tech.kloos.kompound.graph

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.text.KText

internal val LocalKGraphState = compositionLocalOf<KGraphState?> { null }

/**
 * Inside a [KNode]'s body: ports and free content. Port ids refer to the ports declared on the [GraphNode].
 */
@Stable
public interface KNodeScope {
    /**
     * A row with an input port on the node's left edge. [editor] is shown next to the label while the input has no wire
     * (a number field, a slider...) and replaced by the label alone once something is connected.
     */
    @Composable
    public fun Input(port: String, label: String? = null, editor: (@Composable () -> Unit)? = null)

    /** A row with an output port on the node's right edge. */
    @Composable
    public fun Output(port: String, label: String? = null)

    /** Free content with the node's side padding, for anything that is not a port row. */
    @Composable
    public fun Content(content: @Composable ColumnScope.() -> Unit)

    /** The bare port handle, for building your own rows. Place it at the very start (input) or end (output) of a full-width row. */
    @Composable
    public fun PortHandle(port: String, modifier: Modifier = Modifier)
}

/**
 * The frame of a node on a [KNodeGraph]: a title bar you drag by, a body for [content], and the ports declared on [node].
 * Put any Kompound composable in the body (fields, sliders, switches); port rows line up with the wires.
 *
 * Nodes are focusable: arrow keys move the focused node by one grid step and Enter selects it.
 *
 * @param node The node this frame represents (usually the one handed to `nodeContent`).
 * @param title Text in the title bar.
 * @param modifier Modifier applied to the outermost node.
 * @param width Width of the node in world units; its height follows the content.
 * @param style Overrides merged over [KNodeDefaults.style].
 * @param headerStyle Overrides merged over [KNodeDefaults.headerStyle].
 * @param onDoubleClick Called when the node is double-clicked or double-tapped.
 * @param actions Optional content at the end of the title bar (a badge, a menu button).
 * @param content The body; call `Input`, `Output` and `Content` on the scope.
 */
@Composable
public fun KNode(
    node: GraphNode,
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = KNodeDefaults.Width,
    style: Style = Style,
    headerStyle: Style = Style,
    onDoubleClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable KNodeScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = LocalKGraphState.current ?: error("KNode must be used inside KNodeGraph")
    val selected = node.id in state.selection
    val source = remember { MutableInteractionSource() }
    val nodeState = rememberUpdatedStyleState(source) { it.isSelected = selected }
    val headerState = remember { MutableStyleState(null) }
    val scope = remember(node.id, state) { NodeScopeImpl(node.id, state) }
    scope.node = node
    val step = if (state.gridStep > 0f) state.gridStep else 10f
    Column(
        modifier
            .width(width)
            .onSizeChanged { state.sizes[node.id] = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics { contentDescription = title; this.selected = selected }
            .focusable(true, source)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> Offset(-step, 0f)
                    Key.DirectionRight -> Offset(step, 0f)
                    Key.DirectionUp -> Offset(0f, -step)
                    Key.DirectionDown -> Offset(0f, step)
                    Key.Enter -> { state.select(node.id); onDoubleClick?.invoke(); return@onKeyEvent true }
                    else -> return@onKeyEvent false
                }
                val targets = if (node.id in state.selection) state.selection else setOf(node.id)
                state.execute(GraphCommand.MoveNodes(targets.associateWith { delta }))
                true
            }
            .nodeSelectOnClick(state, node.id, onDoubleClick)
            .styleable(nodeState, KNodeDefaults.style(), style),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .nodeDragHandle(state, node.id)
                .styleable(headerState, KNodeDefaults.headerStyle(), headerStyle),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KText(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions?.invoke()
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompositionLocalProvider(LocalKNodeScope provides scope) { scope.content() }
        }
    }
}

internal val LocalKNodeScope = compositionLocalOf<KNodeScope?> { null }

private class NodeScopeImpl(private val id: tech.kloos.kompound.graph.model.NodeId, private val state: KGraphState) : KNodeScope {
    var node: GraphNode = GraphNode(id, "")

    private fun spec(port: String) = node.port(port) ?: error("Node ${node.id} has no port '$port'")

    @Composable
    override fun Input(port: String, label: String?, editor: (@Composable () -> Unit)?) {
        val spec = spec(port)
        val ref = PortRef(id, PortId(port))
        val connected = state.graph.isConnected(ref)
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PortHandle(port, Modifier.halfWidth(PortDirection.Input))
            KText(label ?: spec.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = KNodeDefaults.portLabelStyle())
            if (editor != null && !connected) Box(Modifier.weight(1f)) { editor() }
        }
    }

    @Composable
    override fun Output(port: String, label: String?) {
        val spec = spec(port)
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            KText(label ?: spec.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = KNodeDefaults.portLabelStyle())
            PortHandle(port, Modifier.halfWidth(PortDirection.Output))
        }
    }

    @Composable
    override fun Content(content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }

    @Composable
    override fun PortHandle(port: String, modifier: Modifier) {
        KPortHandle(PortRef(id, PortId(port)), spec(port), modifier)
    }
}

/**
 * Lays the handle out with half its width so its centre sits exactly on the edge of the row: inputs are shifted left by half,
 * outputs keep their place and overflow to the right.
 */
private fun Modifier.halfWidth(direction: PortDirection): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val reported = p.width / 2
    layout(reported, p.height) { p.place(if (direction == PortDirection.Input) -reported else 0, 0) }
}

/**
 * Clicking the node selects it; with Shift, Ctrl or Cmd held it toggles the node in the selection. A second click within the
 * double-click time calls [onDoubleClick].
 */
internal fun Modifier.nodeSelectOnClick(state: KGraphState, id: NodeId, onDoubleClick: (() -> Unit)? = null): Modifier = pointerInput(id, onDoubleClick) {
    var lastUp = -1L
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val additive = currentEvent.isAdditive()
        val up = waitForUpOrCancellation()
        if (up != null) {
            up.consume()
            if (onDoubleClick != null && lastUp >= 0 && up.uptimeMillis - lastUp <= viewConfiguration.doubleTapTimeoutMillis) {
                lastUp = -1L
                onDoubleClick()
            } else {
                lastUp = up.uptimeMillis
                state.select(id, additive)
            }
        }
    }
}

/**
 * Dragging this area moves the node (and the rest of the selection). There is no touch slop: the node follows the
 * pointer from the first pixel instead of lagging behind by the slop distance.
 */
internal fun Modifier.nodeDragHandle(state: KGraphState, id: NodeId): Modifier = pointerInput(id) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val additive = currentEvent.isAdditive()
        var started = false
        val finished = drag(down.id) { change ->
            val delta = change.positionChange()
            if (!started && delta == Offset.Zero) return@drag
            if (!started) {
                // Grabbing an unselected node with Shift held joins the selection instead of replacing it.
                if (additive && id !in state.selection) state.select(id, additive = true)
                state.beginNodeDrag(id)
                started = true
            }
            state.dragNodesBy(delta)
            change.consume()
        }
        if (started) { if (finished) state.endNodeDrag() else state.cancelNodeDrag() }
    }
}
