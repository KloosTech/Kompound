package tech.kloos.kompound.tab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor
import kotlin.math.roundToInt

/**
 * One tab of a [KTabRow].
 *
 * @property label Text of the tab.
 * @property icon Optional slot before the label (a `KIcon`).
 * @property badge Optional small count or text shown after the label (`"3"`).
 * @property enabled A disabled tab is dimmed, cannot be picked and is skipped by the arrow keys.
 * @property contentDescription Overrides what screen readers announce (defaults to the label).
 */
@Immutable
public class KTab(
    public val label: String,
    public val icon: (@Composable () -> Unit)? = null,
    public val badge: String? = null,
    public val enabled: Boolean = true,
    public val contentDescription: String? = null,
)

/**
 * A row of tabs with a sliding underline. The selected tab is announced as a tab with its state; Left and Right move to the previous or
 * next enabled tab (and select it), Home and End jump to the first and last. Show the content of the selected tab yourself
 * (`when (selectedIndex) { ... }`); the row only switches.
 *
 * With [scrollable] the tabs take their natural width and the row scrolls sideways when they do not fit; otherwise they share the width equally.
 *
 * @param tabs The tabs.
 * @param selectedIndex Index of the selected tab.
 * @param onSelectedIndexChange Called with the index the user picked.
 * @param modifier Modifier applied to the outermost node.
 * @param scrollable Natural-width tabs in a horizontally scrolling row.
 * @param style Overrides merged over [KTabRowDefaults.style] (the row).
 * @param tabStyle Overrides merged over [KTabRowDefaults.tabStyle] (each tab).
 */
@Composable
public fun KTabRow(
    tabs: List<KTab>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    style: Style = Style,
    tabStyle: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val rowState = remember { androidx.compose.foundation.style.MutableStyleState(null) }
    val lefts = remember(tabs.size) { IntArray(tabs.size) }
    val widths = remember(tabs.size) { IntArray(tabs.size) }
    var placed by remember(tabs.size) { mutableIntStateOf(0) }
    val left = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    var snapped by remember { mutableStateOf(false) }
    val target = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    LaunchedEffect(target, placed, tabs.size) {
        if (tabs.isEmpty() || widths[target] == 0) return@LaunchedEffect
        if (!snapped) {
            left.snapTo(lefts[target].toFloat()); width.snapTo(widths[target].toFloat()); snapped = true
        } else {
            launch { left.animateTo(lefts[target].toFloat(), spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { width.animateTo(widths[target].toFloat(), spring(stiffness = Spring.StiffnessMediumLow)) }
        }
    }
    val focus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    fun move(to: Int) {
        if (to in tabs.indices && tabs[to].enabled) {
            onSelectedIndexChange(to)
            focus[to].requestFocus()
        }
    }
    fun step(from: Int, direction: Int): Int? {
        var i = from + direction
        while (i in tabs.indices) { if (tabs[i].enabled) return i; i += direction }
        return null
    }
    val scroll = rememberScrollState()
    val indicator = KTabRowDefaults.indicatorStyle()
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(modifier.drawBehind { drawRect(line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }.styleable(rowState, KTabRowDefaults.style(), style)) {
        Box(Modifier.then(if (scrollable) Modifier.horizontalScroll(scroll) else Modifier.fillMaxWidth())) {
            Row(Modifier.selectableGroup().then(if (scrollable) Modifier else Modifier.fillMaxWidth())) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val source = remember { MutableInteractionSource() }
                    val state = rememberUpdatedStyleState(source) {
                        it.isEnabled = tab.enabled
                        it.isSelected = selected
                    }
                    Row(
                        modifier = Modifier
                            .then(if (scrollable) Modifier else Modifier.weight(1f))
                            .onPlaced { c ->
                                val x = c.positionInParent().x.roundToInt()
                                val w = c.size.width
                                if (lefts[index] != x || widths[index] != w) { lefts[index] = x; widths[index] = w; placed++ }
                            }
                            .focusRequester(focus[index])
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                val next = when (event.key) {
                                    Key.DirectionRight -> step(index, 1)
                                    Key.DirectionLeft -> step(index, -1)
                                    Key.MoveHome -> step(-1, 1)
                                    Key.MoveEnd -> step(tabs.size, -1)
                                    else -> return@onKeyEvent false
                                }
                                next?.let(::move)
                                true
                            }
                            .hoverable(source, tab.enabled)
                            .selectable(selected, source, null, tab.enabled, Role.Tab) { onSelectedIndexChange(index) }
                            .semantics { tab.contentDescription?.let { contentDescription = it } }
                            .styleable(state, KTabRowDefaults.tabStyle(), tabStyle),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompositionLocalProvider(LocalKContentColor provides KTabRowDefaults.contentColor(selected, tab.enabled)) {
                            tab.icon?.invoke()
                            KText(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            tab.badge?.let { KBadge(it, tone = KBadgeTone.Primary) }
                        }
                    }
                }
            }
            if (tabs.isNotEmpty() && snapped) {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        drawRect(indicator.color, Offset(left.value, size.height - indicator.height.toPx()), Size(width.value, indicator.height.toPx()))
                    },
                )
            }
        }
    }
}

/** Colour and thickness of the underline of a [KTabRow]. */
internal class TabIndicator(val color: Color, val height: androidx.compose.ui.unit.Dp)

/** Defaults for [KTabRow]. */
public object KTabRowDefaults {
    /** The row: transparent, with a 1dp `outlineVariant` line along the bottom drawn by the component. Add a background or padding here. */
    @Composable
    public fun style(): Style = remember { Style { } }

    /** Underline of the selected tab: 3dp `primary`. */
    @Composable
    internal fun indicatorStyle(): TabIndicator = TabIndicator(MaterialTheme.colorScheme.primary, 3.dp)

    /** The colour of the label and icon of a tab. */
    @Composable
    public fun contentColor(selected: Boolean, enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return when {
            !enabled -> c.onSurface.copy(alpha = l.disabledContent)
            selected -> c.primary
            else -> c.onSurfaceVariant
        }
    }

    /** One tab: label typography, 16dp horizontal padding, 48dp (density scaled) high, interaction layers. */
    @Composable
    public fun tabStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        val density = KompoundTheme.tokens.density
        return remember(c, type, l, density) {
            fun layer(content: Color, alpha: Float) = content.copy(alpha = alpha).compositeOver(Color.Transparent)
            val off = c.onSurfaceVariant
            val on = c.primary
            Style {
                background(Color.Transparent)
                contentColor(off)
                textStyle(type.titleSmall.copy(color = off))
                contentPadding(horizontal = 16.dp, vertical = density.space(12.dp))
                minHeight(density.height(48.dp))
                hovered { background(layer(off, l.hovered)) }
                focused { background(layer(off, l.focused)) }
                pressed { background(layer(off, l.pressed)) }
                selected {
                    contentColor(on)
                    textStyle(type.titleSmall.copy(color = on))
                    hovered { background(layer(on, l.hovered)) }
                    focused { background(layer(on, l.focused)) }
                    pressed { background(layer(on, l.pressed)) }
                }
                disabled {
                    background(Color.Transparent)
                    contentColor(off.copy(alpha = l.disabledContent))
                    textStyle(type.titleSmall.copy(color = off.copy(alpha = l.disabledContent)))
                }
            }
        }
    }
}
