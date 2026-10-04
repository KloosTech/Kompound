package tech.kloos.kompound.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.badge.KBadgeDot
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons

/** One entry of a [KActionMenu]. */
@Immutable
public sealed interface KMenuAction

/**
 * A menu entry that runs [onClick].
 *
 * @property text Label.
 * @property onClick Called when the entry is picked.
 * @property supportingText Optional second line.
 * @property icon Optional icon at the end of the row.
 * @property badge Draws an attention dot before the label.
 * @property selected Marks the entry as the current choice (check mark).
 * @property closeOnClick Closes the menu after [onClick]; turn it off for toggles the user may flip repeatedly.
 * @property enabled Dims the entry and ignores clicks when false.
 */
@Immutable
public class KMenuActionItem(
    public val text: String,
    public val onClick: () -> Unit,
    public val supportingText: String? = null,
    public val icon: ImageVector? = null,
    public val badge: Boolean = false,
    public val selected: Boolean = false,
    public val closeOnClick: Boolean = true,
    public val enabled: Boolean = true,
) : KMenuAction

/** A group that unfolds in place: [text] is its header and [items] appear indented below it when opened. */
@Immutable
public class KMenuActionGroup(public val text: String, public val items: List<KMenuActionItem>) : KMenuAction

/** A thin separator between entries. */
public data object KMenuActionDivider : KMenuAction

/**
 * An icon button that opens a menu of actions: plain entries with an optional second line, icon, attention
 * dot and check mark, unfolding groups and dividers. Build [actions] with `null` for entries that do not apply;
 * they are skipped.
 *
 * @param actions The entries in order. `null` entries are ignored.
 * @param contentDescription Accessibility description of the trigger button (required: it has no label).
 * @param modifier Modifier applied to the outermost node.
 * @param showBadge Draws an attention dot on the trigger, e.g. while an entry needs attention.
 * @param badgeContentDescription Description of the dot for screen readers.
 * @param enabled When false the trigger ignores input.
 * @param style Overrides merged over the trigger button's style.
 * @param menuStyle Overrides merged over [KMenuDefaults.style].
 * @param icon The trigger icon; three dots by default.
 */
@Composable
public fun KActionMenu(
    actions: List<KMenuAction?>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    badgeContentDescription: String? = null,
    enabled: Boolean = true,
    style: Style = Style,
    menuStyle: Style = Style,
    icon: @Composable () -> Unit = { KIcon(KompoundIcons.MoreVertical, contentDescription = null) },
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        KIconButton(onClick = { expanded = !expanded }, contentDescription = contentDescription, enabled = enabled, style = style) { icon() }
        if (showBadge) KBadgeDot(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp), contentDescription = badgeContentDescription)
        KMenu(expanded = expanded, onDismissRequest = { expanded = false }, style = menuStyle) {
            MenuActionEntries(actions) { expanded = false }
        }
    }
}

/** The rows of a menu of [actions] (items, unfolding groups, dividers); [close] is called after an item that closes the menu. Shared by [KActionMenu] and the context menu. */
@Composable
internal fun MenuActionEntries(actions: List<KMenuAction?>, close: () -> Unit) {
    val visible = actions.filterNotNull()
    visible.forEachIndexed { index, action ->
        when (action) {
            is KMenuActionItem -> ActionItem(action, indent = false, close = close)
            is KMenuActionGroup -> ActionGroup(action, close)
            KMenuActionDivider -> if (index != 0 && index != visible.lastIndex) KDivider(Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun ActionItem(action: KMenuActionItem, indent: Boolean, close: () -> Unit) {
    KMenuItem(
        text = action.text,
        onClick = {
            action.onClick()
            if (action.closeOnClick) close()
        },
        modifier = if (indent) Modifier.padding(start = 16.dp) else Modifier,
        enabled = action.enabled,
        selected = action.selected,
        showCheck = action.selected,
        leading = if (action.badge) ({ KBadgeDot() }) else null,
        trailing = action.icon?.let { vector -> { KIcon(vector, contentDescription = null) } },
        supportingText = action.supportingText,
    )
}

@Composable
private fun ActionGroup(group: KMenuActionGroup, close: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    KMenuItem(
        text = group.text,
        onClick = { open = !open },
        role = Role.Button,
        trailing = { KIcon(KompoundIcons.ChevronDown, contentDescription = null, modifier = Modifier.rotate(if (open) 180f else 0f)) },
    )
    if (open) group.items.forEach { ActionItem(it, indent = true, close = close) }
}
