package tech.kloos.kompound.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor
import kotlin.math.roundToInt

/**
 * One destination of a [KNavigationBar], [KNavigationRail] or [KNavigationDrawer].
 *
 * @property key Stable identity; the selection is a key, so items can be reordered or hidden without the selection jumping.
 * @property label Text of the destination (shown under the icon in a bar or rail, beside it in a drawer).
 * @property icon The icon slot (a `KIcon`).
 * @property selectedIcon Shown instead of [icon] while selected (a filled variant); `null` keeps [icon].
 * @property badge A small count or text on the item (`"3"`), or `null`.
 * @property enabled A disabled item is dimmed and cannot be picked.
 * @property contentDescription Overrides what screen readers announce (defaults to the label).
 */
@Immutable
public class KNavItem(
    public val key: String,
    public val label: String,
    public val icon: @Composable () -> Unit,
    public val selectedIcon: (@Composable () -> Unit)? = null,
    public val badge: String? = null,
    public val enabled: Boolean = true,
    public val contentDescription: String? = null,
)

/** An entry of a [KNavigationDrawer]: a destination, a section title or a divider. */
public sealed interface KNavEntry {
    /** A destination. */
    public class Item(public val item: KNavItem) : KNavEntry

    /** A small title above the entries that follow. */
    public class Section(public val title: String) : KNavEntry

    /** A thin separator. */
    public data object Divider : KNavEntry
}

/**
 * Bottom navigation for a handful (3 to 5) of top-level destinations: icon over label, the selected one on a rounded pill.
 * Items are announced as tabs with their selected state; the left and right arrow keys move between enabled items.
 *
 * @param items The destinations.
 * @param selectedKey Key of the selected item, or `null` for none.
 * @param onSelect Called with the key of the picked item.
 * @param modifier Modifier applied to the bar.
 * @param alwaysShowLabel Show every label (default) instead of only the selected item's.
 * @param style Overrides merged over [KNavigationDefaults.barStyle].
 */
@Composable
public fun KNavigationBar(
    items: List<KNavItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    alwaysShowLabel: Boolean = true,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { androidx.compose.foundation.style.MutableStyleState(null) }
    val focus = remember(items.size) { List(items.size) { FocusRequester() } }
    Row(
        modifier.fillMaxWidth().selectableGroup().styleable(state, KNavigationDefaults.barStyle(), style),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavDestination(
                item, item.key == selectedKey, onSelect, vertical = true, showLabel = alwaysShowLabel || item.key == selectedKey,
                modifier = Modifier.weight(1f),
                focus = focus[index], neighbours = { dir -> neighbour(items, index, dir)?.let { focus[it] } }, onMove = { target -> items.getOrNull(target)?.let { onSelect(it.key) } }, indexOf = index, items = items,
            )
        }
    }
}

/**
 * Side navigation for tablets and desktop: a narrow column of destinations (icon over label) with an optional [header] (a menu button or a
 * FAB) and [footer]. Same semantics and keys as [KNavigationBar], with the up and down arrow keys.
 *
 * @param items The destinations.
 * @param selectedKey Key of the selected item, or `null`.
 * @param onSelect Called with the key of the picked item.
 * @param modifier Modifier applied to the rail.
 * @param header Slot above the destinations.
 * @param footer Slot pinned to the bottom.
 * @param alwaysShowLabel Show every label (default) instead of only the selected item's.
 * @param style Overrides merged over [KNavigationDefaults.railStyle].
 */
@Composable
public fun KNavigationRail(
    items: List<KNavItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { androidx.compose.foundation.style.MutableStyleState(null) }
    val focus = remember(items.size) { List(items.size) { FocusRequester() } }
    Column(
        modifier.fillMaxHeight().width(80.dp).styleable(state, KNavigationDefaults.railStyle(), style),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        header?.invoke(this)
        Column(Modifier.weight(1f).selectableGroup().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items.forEachIndexed { index, item ->
                NavDestination(
                    item, item.key == selectedKey, onSelect, vertical = true, showLabel = alwaysShowLabel || item.key == selectedKey,
                    modifier = Modifier.fillMaxWidth(), focus = focus[index], neighbours = { dir -> neighbour(items, index, dir)?.let { focus[it] } },
                    onMove = { target -> items.getOrNull(target)?.let { onSelect(it.key) } }, indexOf = index, items = items, rail = true,
                )
            }
        }
        footer?.invoke(this)
    }
}

/**
 * A permanent navigation list for wide screens: destinations with icon and label side by side, section titles and dividers, an optional
 * [header]. For phones put it into a [KModalNavigationDrawer].
 *
 * @param entries Destinations ([KNavEntry.Item]), section titles and dividers in order.
 * @param selectedKey Key of the selected destination, or `null`.
 * @param onSelect Called with the key of the picked destination.
 * @param modifier Modifier applied to the drawer.
 * @param header Slot above the entries (an app title, an avatar).
 * @param style Overrides merged over [KNavigationDefaults.drawerStyle].
 */
@Composable
public fun KNavigationDrawer(
    entries: List<KNavEntry>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { androidx.compose.foundation.style.MutableStyleState(null) }
    val items = entries.filterIsInstance<KNavEntry.Item>().map { it.item }
    val focus = remember(items.size) { List(items.size) { FocusRequester() } }
    Column(modifier.widthIn(min = 240.dp, max = 360.dp).fillMaxHeight().styleable(state, KNavigationDefaults.drawerStyle(), style).verticalScroll(rememberScrollState())) {
        header?.invoke(this)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            var itemIndex = 0
            for (entry in entries) {
                when (entry) {
                    is KNavEntry.Item -> {
                        val index = itemIndex++
                        NavDestination(
                            entry.item, entry.item.key == selectedKey, onSelect, vertical = false, showLabel = true, modifier = Modifier.fillMaxWidth(),
                            focus = focus[index], neighbours = { dir -> neighbour(items, index, dir)?.let { focus[it] } },
                            onMove = { target -> items.getOrNull(target)?.let { onSelect(it.key) } }, indexOf = index, items = items,
                        )
                    }
                    is KNavEntry.Section -> KText(entry.title, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), maxLines = 1, style = KNavigationDefaults.sectionStyle())
                    KNavEntry.Divider -> KDivider(Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

/**
 * Wraps [content] and slides a navigation [drawer] in from the start edge over a dimming scrim. A tap on the scrim, the Escape key or the
 * back action your app maps to [onDismissRequest] closes it. Put a [KNavigationDrawer] inside [drawer].
 *
 * @param open Whether the drawer is shown.
 * @param onDismissRequest Called when the user asks to close it.
 * @param drawer The drawer content.
 * @param modifier Modifier applied to the container.
 * @param title Announced when the drawer opens (default: "Open navigation menu").
 * @param closeDescription Description of the scrim, which closes the drawer.
 * @param content The screen behind the drawer.
 */
@Composable
public fun KModalNavigationDrawer(
    open: Boolean,
    onDismissRequest: () -> Unit,
    drawer: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    title: String = KompoundTheme.strings.openNavigation,
    closeDescription: String = KompoundTheme.strings.closeNavigation,
    content: @Composable () -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val progress = remember { Animatable(if (open) 1f else 0f) }
    val duration = KompoundTheme.tokens.motion.durationMedium
    LaunchedEffect(open) { progress.animateTo(if (open) 1f else 0f, tween(duration)) }
    BoxWithConstraints(modifier) {
        content()
        if (open || progress.value > 0f) {
            val focus = remember { FocusRequester() }
            val scrim = MaterialTheme.colorScheme.scrim
            Box(
                Modifier.fillMaxSize()
                    .background(scrim.copy(alpha = 0.4f * progress.value))
                    .semantics { contentDescription = closeDescription }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDismissRequest),
            )
            var drawerWidth by remember { mutableStateOf(0) }
            val maxDrawer = maxWidth * 0.85f
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 360.dp.coerceAtMost(maxDrawer))
                    .onSizeChanged { drawerWidth = it.width }
                    .offset { IntOffset(((progress.value - 1f) * drawerWidth).roundToInt(), 0) }
                    .semantics { paneTitle = title }
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { onDismissRequest(); true } else false
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                content = drawer,
            )
            LaunchedEffect(open) { if (open) runCatching { focus.requestFocus() } }
        }
    }
}

private fun neighbour(items: List<KNavItem>, from: Int, direction: Int): Int? {
    var i = from + direction
    while (i in items.indices) { if (items[i].enabled) return i; i += direction }
    return null
}

@Composable
private fun NavDestination(
    item: KNavItem,
    selected: Boolean,
    onSelect: (String) -> Unit,
    vertical: Boolean,
    showLabel: Boolean,
    modifier: Modifier,
    focus: FocusRequester,
    neighbours: (Int) -> FocusRequester?,
    onMove: (Int) -> Unit,
    indexOf: Int,
    items: List<KNavItem>,
    rail: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val state = rememberUpdatedStyleState(source) {
        it.isEnabled = item.enabled
        it.isSelected = selected
    }
    val prev = if (vertical && !rail) Key.DirectionLeft else if (vertical) Key.DirectionUp else Key.DirectionUp
    val next = if (vertical && !rail) Key.DirectionRight else Key.DirectionDown
    val base = modifier
        .focusRequester(focus)
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val dir = when (event.key) { prev -> -1; next -> 1; else -> return@onKeyEvent false }
            val target = neighbour(items, indexOf, dir) ?: return@onKeyEvent true
            onMove(target)
            neighbours(dir)?.requestFocus()
            true
        }
        .hoverable(source, item.enabled)
        .selectable(selected, source, null, item.enabled, Role.Tab) { onSelect(item.key) }
        .semantics { item.contentDescription?.let { contentDescription = it } }
    val tint = KNavigationDefaults.contentColor(selected, item.enabled)
    if (vertical) {
        Column(base.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompositionLocalProvider(LocalKContentColor provides tint) {
                Box(Modifier.width(64.dp).styleable(state, KNavigationDefaults.pillStyle()), contentAlignment = Alignment.Center) {
                    (if (selected) item.selectedIcon ?: item.icon else item.icon)()
                    item.badge?.let { KBadge(it, Modifier.align(Alignment.TopEnd).padding(end = 8.dp), tone = KBadgeTone.Error) }
                }
                if (showLabel) KText(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = KNavigationDefaults.labelStyle(selected, item.enabled))
            }
        }
    } else {
        Row(
            base.styleable(state, KNavigationDefaults.rowStyle()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalKContentColor provides tint) {
                (if (selected) item.selectedIcon ?: item.icon else item.icon)()
                KText(item.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.badge?.let { KBadge(it, tone = KBadgeTone.Primary) }
            }
        }
    }
}

/** Defaults for the navigation components. */
public object KNavigationDefaults {
    /** The colour of an item's icon and label. */
    @Composable
    public fun contentColor(selected: Boolean, enabled: Boolean = true): Color {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return when {
            !enabled -> c.onSurface.copy(alpha = l.disabledContent)
            selected -> c.onSecondaryContainer
            else -> c.onSurfaceVariant
        }
    }

    /** The bottom bar: `surfaceContainer`, 80dp (density scaled) high. */
    @Composable
    public fun barStyle(): Style {
        val c = MaterialTheme.colorScheme
        val density = KompoundTheme.tokens.density
        return remember(c, density) { Style { background(c.surfaceContainer); minHeight(density.height(80.dp)); contentPadding(horizontal = 8.dp, vertical = 4.dp) } }
    }

    /** The side rail: `surface`, 80dp wide, 12dp vertical padding. */
    @Composable
    public fun railStyle(): Style {
        val c = MaterialTheme.colorScheme
        return remember(c) { Style { background(c.surface); contentPadding(horizontal = 0.dp, vertical = 12.dp) } }
    }

    /** The permanent drawer: `surfaceContainerLow` with 12dp padding. */
    @Composable
    public fun drawerStyle(): Style {
        val c = MaterialTheme.colorScheme
        return remember(c) { Style { background(c.surfaceContainerLow); contentPadding(12.dp) } }
    }

    /** The rounded 32dp pill behind a bar or rail icon: `secondaryContainer` while selected, interaction layers otherwise. */
    @Composable
    internal fun pillStyle(): Style {
        val c = MaterialTheme.colorScheme
        val l = KompoundTheme.tokens.stateLayer
        return remember(c, l) {
            fun layer(content: Color, alpha: Float) = content.copy(alpha = alpha).compositeOver(Color.Transparent)
            val off = c.onSurfaceVariant
            Style {
                background(Color.Transparent)
                shape(CircleShape)
                minHeight(32.dp)
                hovered { background(layer(off, l.hovered)) }
                focused { background(layer(off, l.focused)) }
                pressed { background(layer(off, l.pressed)) }
                selected { background(c.secondaryContainer) }
                disabled { background(Color.Transparent) }
            }
        }
    }

    /** A drawer row: a rounded pill, `secondaryContainer` while selected. */
    @Composable
    internal fun rowStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        val l = KompoundTheme.tokens.stateLayer
        val density = KompoundTheme.tokens.density
        return remember(c, type, l, density) {
            fun layer(content: Color, alpha: Float) = content.copy(alpha = alpha).compositeOver(Color.Transparent)
            val off = c.onSurfaceVariant
            val on = c.onSecondaryContainer
            Style {
                background(Color.Transparent)
                shape(CircleShape)
                contentColor(off)
                textStyle(type.labelLarge.copy(color = off))
                contentPadding(horizontal = 16.dp, vertical = density.space(8.dp))
                minHeight(density.height(56.dp))
                hovered { background(layer(off, l.hovered)) }
                focused { background(layer(off, l.focused)) }
                pressed { background(layer(off, l.pressed)) }
                selected {
                    background(c.secondaryContainer)
                    contentColor(on)
                    textStyle(type.labelLarge.copy(color = on))
                    hovered { background(layer(on, l.hovered).compositeOver(c.secondaryContainer)) }
                }
                disabled { background(Color.Transparent); contentColor(off.copy(alpha = l.disabledContent)); textStyle(type.labelLarge.copy(color = off.copy(alpha = l.disabledContent))) }
            }
        }
    }

    /** The label under a bar or rail icon. */
    @Composable
    internal fun labelStyle(selected: Boolean, enabled: Boolean): Style {
        val type = MaterialTheme.typography
        val color = contentColor(selected, enabled).let { if (selected && enabled) MaterialTheme.colorScheme.onSurface else it }
        return remember(type, color) { Style { contentColor(color); textStyle(type.labelMedium.copy(color = color)) } }
    }

    /** The title of a drawer section. */
    @Composable
    internal fun sectionStyle(): Style {
        val type = MaterialTheme.typography
        val c = MaterialTheme.colorScheme.onSurfaceVariant
        return remember(type, c) { Style { contentColor(c); textStyle(type.titleSmall.copy(color = c)) } }
    }
}
