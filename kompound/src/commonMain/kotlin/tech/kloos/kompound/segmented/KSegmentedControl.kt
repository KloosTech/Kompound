package tech.kloos.kompound.segmented

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
 * Segments share the width of the widest one.
 *
 * @param options Labels of the segments, in order.
 * @param selectedIndex Index of the selected segment.
 * @param onSelectedIndexChange Called with the index the user picked.
 * @param modifier Modifier applied to the container.
 * @param style Overrides merged over [KSegmentedControlDefaults.style] (the container).
 * @param segmentStyle Overrides merged over [KSegmentedControlDefaults.segmentStyle] (each segment); use a
 * `selected { }` block for the selected look.
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
    enabled: Boolean = true,
    segment: @Composable RowScope.(index: Int, label: String) -> Unit = { _, label -> KText(label, maxLines = 1) },
) {
    remember { KompoundStyles.ensureEnabled() }
    val containerState = remember { MutableStyleState(null) }
    Row(
        modifier = modifier.width(IntrinsicSize.Max).selectableGroup().styleable(containerState, KSegmentedControlDefaults.style(), style),
    ) {
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

    /** One segment: transparent pill, `secondaryContainer` when selected, with interaction layers. */
    @Composable
    public fun segmentStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, type, l) {
            fun layer(content: Color, alpha: Float, over: Color) = content.copy(alpha = alpha).compositeOver(over)
            val off = c.onSurface
            val on = c.onSecondaryContainer
            Style {
                background(Color.Transparent)
                contentColor(off)
                textStyle(type.labelLarge.copy(color = off))
                shape(CircleShape)
                contentPadding(horizontal = 16.dp, vertical = 8.dp)
                minHeight(32.dp)
                hovered { background(layer(off, l.hovered, Color.Transparent)) }
                focused { background(layer(off, l.focused, Color.Transparent)) }
                pressed { background(layer(off, l.pressed, Color.Transparent)) }
                selected {
                    background(c.secondaryContainer)
                    contentColor(on)
                    textStyle(type.labelLarge.copy(color = on))
                    hovered { background(layer(on, l.hovered, c.secondaryContainer)) }
                    focused { background(layer(on, l.focused, c.secondaryContainer)) }
                    pressed { background(layer(on, l.pressed, c.secondaryContainer)) }
                }
                disabled {
                    background(Color.Transparent)
                    contentColor(off.copy(alpha = l.disabledContent))
                    textStyle(type.labelLarge.copy(color = off.copy(alpha = l.disabledContent)))
                    selected { background(c.onSurface.copy(alpha = l.disabledContainer)) }
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
