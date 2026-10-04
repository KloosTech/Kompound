package tech.kloos.kompound.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.text.KText

/**
 * A compact choice field sized for a node row: the selected option and a chevron; a click opens a menu of [options]. Use it in a
 * node's `Content`, or as the `editor` of an `Input` (it is hidden once a wire is connected, like any editor).
 *
 * @param options The choices.
 * @param selected The current choice, or `null` to show [placeholder].
 * @param onSelect Called with the picked option (also when it is the one already selected).
 * @param optionLabel Text of an option.
 * @param placeholder Shown while nothing is selected.
 * @param enabled When `false` the menu cannot be opened. Pass `!state.readOnly` in a read-only editor.
 */
@Composable
public fun <T> KNodeSelect(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    placeholder: String = "Select",
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val text = selected?.let(optionLabel) ?: placeholder
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surfaceContainerHighest.copy(alpha = if (enabled) 1f else 0.5f))
                .border(1.dp, scheme.outlineVariant, shape)
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .semantics { stateDescription = text }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .alpha(if (enabled) 1f else 0.6f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KText(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            KIcon(GraphIcons.ExpandMore, null, Modifier.size(18.dp))
        }
        KMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in options) {
                KMenuItem(optionLabel(option), onClick = { open = false; onSelect(option) }, selected = option == selected, showCheck = true)
            }
        }
    }
}

/**
 * A labelled [KNodeSelect] row for a node's body: the label on the left, the choice field filling the rest. Put it inside
 * [KNodeScope.Content] or call it directly in a node (it adds the node's side padding).
 */
@Composable
public fun <T> KNodeScope.Select(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KText(label, maxLines = 1, style = KNodeDefaults.portLabelStyle())
        KNodeSelect(options, selected, onSelect, Modifier.weight(1f), optionLabel, enabled = enabled)
    }
}
