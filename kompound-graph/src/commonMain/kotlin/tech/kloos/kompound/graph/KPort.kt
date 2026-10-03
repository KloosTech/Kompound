package tech.kloos.kompound.graph

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec

/**
 * The round handle of a port. It reports its position to the editor, starts a wire when dragged and highlights while
 * a wire is being dragged (valid targets ringed, the snapped target filled, others dimmed). Keyboard users focus it
 * and press Enter or Space: on an output that starts a wire, on an input it ends the pending wire there.
 */
@Composable
internal fun KPortHandle(ref: PortRef, spec: PortSpec, modifier: Modifier = Modifier) {
    val state = LocalKGraphState.current ?: error("KNode must be used inside KNodeGraph")
    val colors = KNodeGraphDefaults.portPalette()
    val colour = colors[KNodeGraphDefaults.paletteIndex(spec.type, colors.size)]
    val track = MaterialTheme.colorScheme.surface
    val dim = MaterialTheme.colorScheme.outlineVariant
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    // The editor's layer may appear after the port: report again once it is there.
    val tick = state.layerTick
    androidx.compose.runtime.LaunchedEffect(tick) { coords[0]?.let { state.reportPort(ref, it) } }

    val description = buildString {
        append(spec.label).append(", ").append(if (spec.direction == PortDirection.Input) "input" else "output").append(", ").append(spec.type.id)
        append(if (state.graph.isConnected(ref)) ", connected" else ", not connected")
    }
    val size = KNodeDefaults.PortSize
    Box(
        modifier
            .size(KNodeDefaults.PortTouchSize)
            .onGloballyPositioned { c -> coords[0] = c; state.reportPort(ref, c) }
            .pointerInput(ref) {
                detectDragGestures(
                    onDragStart = { state.beginWire(ref) },
                    onDragEnd = { state.endWire() },
                    onDragCancel = { state.cancelWire() },
                ) { change, drag ->
                    change.consume()
                    val w = state.wire
                    if (w != null) state.updateWire(w.pointer + drag)
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.Spacebar)) { activate(state, ref, spec); true } else false
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { activate(state, ref, spec); true }
            }
            .drawBehind {
                val wire = state.wire
                val connected = state.graph.isConnected(ref)
                val isCompatible = wire != null && ref in wire.compatible
                val isTarget = wire != null && wire.target == ref
                val isSource = wire != null && wire.from == ref
                val dimmed = wire != null && !isCompatible && !isSource
                val r = size.toPx() / 2f * (if (isTarget) 1.35f else 1f)
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                val fill = if (dimmed) dim else colour
                if (connected || isTarget || isSource) drawCircle(fill, r, centre)
                else drawCircle(track, r, centre)
                drawCircle(fill, r, centre, style = Stroke(width = 2.dp.toPx()))
                if (isCompatible && !isTarget) drawCircle(colour.copy(alpha = 0.35f), r + 5.dp.toPx(), centre, style = Stroke(width = 2.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {}
}

private fun activate(state: KGraphState, ref: PortRef, spec: PortSpec) {
    val pending = state.wire
    if (pending == null) {
        if (spec.direction == PortDirection.Output) state.beginKeyboardWire(ref)
    } else if (pending.from == ref) state.cancelWire()
    else state.completeWire(ref)
}
