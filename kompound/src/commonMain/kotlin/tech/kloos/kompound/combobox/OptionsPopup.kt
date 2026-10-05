package tech.kloos.kompound.combobox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.menu.DropdownPositionProvider
import tech.kloos.kompound.menu.KMenuDefaults
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.text.KText
import androidx.compose.foundation.style.Style
import androidx.compose.ui.semantics.Role

/** One row of an [OptionsPopup]. */
internal class OptionRow(val label: String, val supporting: String? = null, val enabled: Boolean = true)

/**
 * The suggestion list under a text field. It never takes focus (the field keeps typing); the [active] row is tinted and kept in view. Place it
 * inside the Box that wraps the field: it opens below, flipped above when there is no room.
 */
@Composable
internal fun OptionsPopup(
    rows: List<OptionRow>,
    active: Int,
    width: Dp,
    emptyText: String?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    maxHeight: Dp = 280.dp,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val list = rememberLazyListState()
    LaunchedEffect(active, rows.size) {
        if (active in rows.indices) {
            val visible = list.layoutInfo.visibleItemsInfo
            if (visible.isEmpty() || active <= visible.first().index || active >= visible.last().index) list.scrollToItem((active - 1).coerceAtLeast(0))
        }
    }
    Popup(
        popupPositionProvider = DropdownPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = true),
    ) {
        Box(Modifier.padding(top = 4.dp)) {
            if (rows.isEmpty()) {
                if (emptyText != null) KText(emptyText, Modifier.width(width).styleable(state, KMenuDefaults.style()).padding(16.dp))
            } else {
                LazyColumn(Modifier.width(width).heightIn(max = maxHeight).styleable(state, KMenuDefaults.style()), list) {
                    itemsIndexed(rows) { i, row ->
                        KMenuItem(row.label, { onPick(i) }, enabled = row.enabled, selected = i == active, role = Role.Button, supportingText = row.supporting)
                    }
                }
            }
        }
    }
}
