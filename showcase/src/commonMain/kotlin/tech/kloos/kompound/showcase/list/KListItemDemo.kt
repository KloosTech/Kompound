package tech.kloos.kompound.showcase.list

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.showcase.DemoIcons

@KompoundDemo(
    id = "list.item",
    title = "KListItem",
    description = "List row with overline, headline, supporting text, leading and trailing slots, optionally clickable or selectable.",
    category = KompoundCategory.Display,
    tags = ["list", "row", "item", "selection", "cell"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KListItemDemo() {
    val enabled = boolControl("Enabled", true)
    val selectable = boolControl("Selectable rows", false)
    var selected by remember { mutableIntStateOf(0) }
    val people = listOf("Ada Lovelace" to "Mathematician", "Grace Hopper" to "Admiral", "Alan Turing" to "Cryptanalyst")
    Column {
        people.forEachIndexed { i, (name, role) ->
            KListItem(
                headline = name, supporting = role, overline = if (i == 0) "Pioneers" else null,
                leading = { KAvatar(name) },
                trailing = if (i == 1) ({ KBadge("3") }) else ({ KIcon(DemoIcons.Check, null) }),
                onClick = { selected = i },
                selected = if (selectable) selected == i else null,
                enabled = enabled,
            )
            KDivider()
        }
        KListItem(
            headline = "With a bottom slot", supporting = "Chips under the text",
            bottom = { KChip("Tag", onClick = {}) }, enabled = enabled,
        )
    }
}
