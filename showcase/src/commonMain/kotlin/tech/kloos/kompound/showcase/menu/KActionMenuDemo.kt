package tech.kloos.kompound.showcase.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.menu.KActionMenu
import tech.kloos.kompound.menu.KMenuActionDivider
import tech.kloos.kompound.menu.KMenuActionGroup
import tech.kloos.kompound.menu.KMenuActionItem
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

private const val Usage_menu_actions = """import tech.kloos.kompound.menu.KActionMenu
import tech.kloos.kompound.menu.KMenuActionDivider
import tech.kloos.kompound.menu.KMenuActionGroup
import tech.kloos.kompound.menu.KMenuActionItem

var dark by remember { mutableStateOf(false) }

KActionMenu(
    contentDescription = "More actions",
    showBadge = hasUnsyncedChanges,            // attention dot on the trigger
    actions = listOf(
        KMenuActionItem("Sync now", onClick = { sync() }, supportingText = "Last sync 5 min ago", badge = hasUnsyncedChanges),
        KMenuActionItem("Dark mode", onClick = { dark = !dark }, selected = dark, closeOnClick = false),
        KMenuActionDivider,
        KMenuActionGroup("Sort by", listOf(KMenuActionItem("Name", { sortBy("name") }), KMenuActionItem("Date", { sortBy("date") }))),
        if (isAdmin) KMenuActionItem("Delete all", onClick = { deleteAll() }) else null,   // null entries are skipped
    ),
)"""

@KompoundDemo(
    id = "menu.actions",
    title = "KActionMenu",
    description = "Icon button that opens a menu of actions with supporting text, attention dots, check marks, groups and dividers.",
    category = KompoundCategory.Overlays,
    tags = ["menu", "actions", "overflow", "more", "toolbar", "badge"],
    since = "0.1.0",
    usage = Usage_menu_actions,
)
@Composable
fun DemoScope.KActionMenuDemo() {
    val badge = boolControl("Badge on trigger", true)
    val supporting = boolControl("Supporting text", true)
    val icons = boolControl("Icons", true)
    val group = boolControl("Group", true)
    var dark by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf("Nothing picked yet") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KActionMenu(
            contentDescription = "More actions",
            showBadge = badge,
            badgeContentDescription = "Needs attention",
            actions = listOf(
                KMenuActionItem("Sync now", { last = "Sync now" }, supportingText = if (supporting) "Last sync 5 min ago" else null, badge = badge, icon = if (icons) DemoIcons.Star else null),
                KMenuActionItem("Dark mode", { dark = !dark; last = "Dark mode ${if (dark) "on" else "off"}" }, selected = dark, closeOnClick = false),
                KMenuActionDivider,
                if (group) KMenuActionGroup("Sort by", listOf(KMenuActionItem("Name", { last = "Sort by name" }), KMenuActionItem("Date", { last = "Sort by date" }))) else null,
                KMenuActionItem("Delete all", { last = "Delete all" }, icon = if (icons) DemoIcons.Check else null),
            ),
        )
        KText(last)
    }
}
