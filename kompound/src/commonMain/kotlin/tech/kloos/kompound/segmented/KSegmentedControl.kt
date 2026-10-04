package tech.kloos.kompound.segmented

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.focused
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Row of mutually exclusive options; exactly one is selected. Announced as a group of radio buttons.
 * Segments share the width of the widest one. The selection highlight is a pill that slides to the newly
 * selected segment.
 *
 * @param options Labels of the segments, in order.
 * @param selectedIndex Index of the selected segment.
 * @param onSelectedIndexChange Called with the index the user picked.
 * @param modifier Modifier applied to the container.
 * @param style Overrides merged over [KSegmentedControlDefaults.style] (the container).
 * @param segmentStyle Overrides merged over [KSegmentedControlDefaults.segmentStyle] (each segment): text,
 * padding and the hover, focus and press layers. A `selected { }` block with a `background` is drawn over the
 * sliding pill and does not animate; use [indicatorStyle] for the pill.
 * @param indicatorStyle Overrides merged over [KSegmentedControlDefaults.indicatorStyle] (the sliding pill).
 * @param enabled When false no segment can be picked.
 * @param segment Content of one segment; defaults to a [KText] label. Receives the index and label.
 */
@Composable
public fun KSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    segmentStyle: Style = Style,
    indicatorStyle: Style = Style,
    enabled: Boolean = true,
    segment: @Composable RowScope.(index: Int, label: String) -> Unit = { _, label -> KText(label, maxLines = 1) },
) {
    remember { KompoundStyles.ensureEnabled() }
    val containerState = remember { MutableStyleState(null) }
    val indicatorState = rememberUpdatedStyleState(null) { it.isEnabled = enabled }
    // Position and width of every segment inside the row; the pill animates between them.
    val lefts = remember(options.size) { IntArray(options.size) }
    val widths = remember(options.size) { IntArray(options.size) }
    var placed by remember(options.size) { mutableIntStateOf(0) }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var snapped by remember { mutableStateOf(false) }
    val target = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    LaunchedEffect(target, placed, options.size) {
        if (options.isEmpty() || widths[target] == 0) return@LaunchedEffect
        if (!snapped) {          // first layout: appear in place instead of sliding in from the corner
            left.snapTo(lefts[target].toFloat()); width.snapTo(widths[target].toFloat()); snapped = true
        } else {
            launch { left.animateTo(lefts[target].toFloat(), spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { width.animateTo(widths[target].toFloat(), spring(stiffness = Spring.StiffnessMediumLow)) }
        }
    }
    Row(
        modifier = modifier.width(IntrinsicSize.Max).selectableGroup().styleable(containerState, KSegmentedControlDefaults.style(), style),
    ) {
        Box(Modifier.height(IntrinsicSize.Min)) {
            if (options.isNotEmpty() && snapped) {
                Box(
                    Modifier
                        .layout { measurable, constraints ->
                            val w = width.value.roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                            layout(0, 0) { placeable.place(left.value.roundToInt(), 0) }
                        }
                        .fillMaxHeight()
                        .styleable(indicatorState, KSegmentedControlDefaults.indicatorStyle(), indicatorStyle),
                )
            }
            Row {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    val source = remember { MutableInteractionSource() }
                    val state = rememberUpdatedStyleState(source) {
                        it.isEnabled = enabled
                        it.isSelected = selected
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .onPlaced { coordinates ->
                                val x = coordinates.positionInParent().x.roundToInt()
                                val w = coordinates.size.width
                                if (lefts[index] != x || widths[index] != w) { lefts[index] = x; widths[index] = w; placed++ }
                            }
                            .hoverable(source, enabled)
                            .selectable(selected, source, null, enabled, Role.RadioButton) { onSelectedIndexChange(index) }
                            .styleable(state, KSegmentedControlDefaults.segmentStyle(), segmentStyle),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompositionLocalProvider(LocalKContentColor provides KSegmentedControlDefaults.contentColor(selected, enabled)) {
                            segment(index, label)
                        }
                    }
                }
            }
        }
    }
}

/** Defaults for [KSegmentedControl]. */
public object KSegmentedControlDefaults {
    /** Container: a pill with a 1dp outline and 2dp inner padding. */
    @Composable
    public fun style(): Style {
        val outline = MaterialTheme.colorScheme.outline
        return remember(outline) {
            Style {
                shape(CircleShape)
                borderWidth(1.dp)
                borderColor(outline)
                contentPadding(2.dp)
            }
        }
    }

    /** The sliding selection pill: `secondaryContainer`, or a faint `onSurface` tint when disabled. */
    @Composable
    public fun indicatorStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            Style {
                background(c.secondaryContainer)
                shape(CircleShape)
                disabled { background(c.onSurface.copy(alpha = l.disabledContainer)) }
            }
        }
    }

    /** One segment: transparent pill with interaction layers; the selected segment's text turns `onSecondaryContainer`. */
    @Composable
    public fun segmentStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        val density = KompoundTheme.tokens.density
        return remember(c, type, l, density) {
            fun layer(content: Color, alpha: Float, over: Color) = content.copy(alpha = alpha).compositeOver(over)
            val off = c.onSurface
            val on = c.onSecondaryContainer
            Style {
                background(Color.Transparent)
                contentColor(off)
                textStyle(type.labelLarge.copy(color = off))
                shape(CircleShape)
                contentPadding(horizontal = 16.dp, vertical = density.space(8.dp))
                minHeight(density.height(32.dp))
                hovered { background(layer(off, l.hovered, Color.Transparent)) }
                focused { background(layer(off, l.focused, Color.Transparent)) }
                pressed { background(layer(off, l.pressed, Color.Transparent)) }
                selected {
                    // The pill behind it is the sliding indicator; here only text and the interaction layers change.
                    contentColor(on)
                    textStyle(type.labelLarge.copy(color = on))
                    hovered { background(on.copy(alpha = l.hovered)) }
                    focused { background(on.copy(alpha = l.focused)) }
                    pressed { background(on.copy(alpha = l.pressed)) }
                }
                disabled {
                    background(Color.Transparent)
                    contentColor(off.copy(alpha = l.disabledContent))
                    textStyle(type.labelLarge.copy(color = off.copy(alpha = l.disabledContent)))
                }
            }
        }
    }

    /** The colour text and icons in a segment get; icons read it through `LocalKContentColor`. */
    @Composable
    public fun contentColor(selected: Boolean, enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        return when {
            !enabled -> c.onSurface.copy(alpha = KompoundTheme.tokens.stateLayer.disabledContent)
            selected -> c.onSecondaryContainer
            else -> c.onSurface
        }
    }
}
