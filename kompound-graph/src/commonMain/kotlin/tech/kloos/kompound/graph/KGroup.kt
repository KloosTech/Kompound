package tech.kloos.kompound.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.graph.model.GroupId
import tech.kloos.kompound.graph.model.NodeGroup
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.text.KText

/** Defaults for the group frames of [KNodeGraph]. */
public object KGroupDefaults {
    /** Title bar text: `titleSmall` in `onSurface`. */
    @Composable
    public fun titleStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurface); textStyle(type.titleSmall.copy(color = c.onSurface)) } }
    }

    /** The accent colour of [group] from the editor's palette. */
    @Composable
    public fun accent(group: NodeGroup): Color {
        val palette = KNodeGraphDefaults.portPalette()
        return palette[((group.color % palette.size) + palette.size) % palette.size]
    }
}

/**
 * The frame the editor draws around the members of [group]: a tinted, outlined rectangle with a title bar that carries a collapse
 * button, the title and the member count. Drag the title bar to move the whole group, click it to select the members. When the group is
 * collapsed it is one compact box and the members are hidden.
 */
@Composable
internal fun KGroupFrame(group: NodeGroup, members: Int, modifier: Modifier = Modifier) {
    remember { KompoundStyles.ensureEnabled() }
    val state = LocalKGraphState.current ?: error("KGroupFrame must be used inside KNodeGraph")
    val accent = KGroupDefaults.accent(group)
    val scheme = MaterialTheme.colorScheme
    val titleState = remember { MutableStyleState(null) }
    val description = "Group ${group.title}, $members nodes, ${if (group.collapsed) "collapsed" else "expanded"}"
    Box(
        modifier
            .semantics { contentDescription = description }
            .drawBehind {
                val radius = CornerRadius(12.dp.toPx())
                if (group.collapsed) drawRoundRect(scheme.surfaceContainerHigh, cornerRadius = radius)
                drawRoundRect(accent.copy(alpha = if (group.collapsed) 0.18f else 0.08f), cornerRadius = radius)
                drawRoundRect(accent.copy(alpha = 0.55f), cornerRadius = radius, style = Stroke(width = 1.5.dp.toPx()))
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(GroupHeader.dp)
                .groupHandle(state, group.id)
                .padding(horizontal = 4.dp)
                .styleable(titleState, KGroupDefaults.titleStyle()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KIconButton({ state.toggleCollapsed(group.id) }, if (group.collapsed) "Expand ${group.title}" else "Collapse ${group.title}") {
                KIcon(if (group.collapsed) GraphIcons.ChevronRight else GraphIcons.ExpandMore, null)
            }
            KText(group.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            KText("$members", style = KNodeDefaults.portLabelStyle(), modifier = Modifier.padding(end = 8.dp))
        }
    }
}

/** Dragging moves the group's members together; a click selects them. */
private fun Modifier.groupHandle(state: KGraphState, id: GroupId): Modifier = pointerInput(id) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var started = false
        val finished = drag(down.id) { change ->
            val delta = change.positionChange()
            if (!started && delta == Offset.Zero) return@drag
            if (!started) { state.beginGroupDrag(id); started = true }
            state.dragNodesBy(delta)
            change.consume()
        }
        if (started) { if (finished) state.endNodeDrag() else state.cancelNodeDrag() }
        else if (finished) state.selectGroup(id)
    }
}
