package tech.kloos.kompound.graph

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.text.KText
import kotlin.math.roundToInt

/** True inside content that is (being) collapsed: the ports in it cannot take part in wiring. */
internal val LocalCollapsed = compositionLocalOf { false }

/**
 * Content that folds away. The content stays composed (so its ports keep reporting where they are): the layout height and a vertical
 * scale go from 1 to 0, which also pulls the ports' wire anchors up to the fold. While collapsed it is invisible to accessibility and
 * its ports are not offered as wire targets.
 */
@Composable
internal fun CollapseContainer(expanded: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val fraction by animateFloatAsState(if (expanded) 1f else 0f, tween(180), label = "collapse")
    val parentCollapsed = LocalCollapsed.current
    CompositionLocalProvider(LocalCollapsed provides (parentCollapsed || !expanded)) {
        Column(
            modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val height = (placeable.height * fraction).roundToInt()
                    layout(placeable.width, height) {
                        placeable.placeWithLayer(0, 0) {
                            scaleY = fraction
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            alpha = fraction
                        }
                    }
                }
                // Only clip while folded or folding: ports overhang the node's edges, and a clip would swallow their touches.
                .then(if (fraction < 1f) Modifier.clipToBounds() else Modifier)
                .then(if (!expanded && fraction == 0f) Modifier.clearAndSetSemantics { } else Modifier),
            content = content,
        )
    }
}

/** The clickable title row of a collapsible section: a chevron and the [title]. */
@Composable
internal fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedDescription: String,
    collapsedDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) expandedDescription else collapsedDescription }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIcon(if (expanded) GraphIcons.ExpandMore else GraphIcons.ChevronRight, null, Modifier.size(18.dp))
        KText(title, maxLines = 1, style = KNodeDefaults.portLabelStyle())
    }
}

/** Registers [ref] as hidden (not a wire target) while the surrounding content is collapsed. */
@Composable
internal fun HideWhileCollapsed(state: KGraphState, ref: tech.kloos.kompound.graph.model.PortRef) {
    val collapsed = LocalCollapsed.current
    DisposableEffect(ref, collapsed) {
        if (collapsed) state.hiddenPorts[ref] = true
        onDispose { state.hiddenPorts.remove(ref) }
    }
}
