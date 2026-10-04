package tech.kloos.kompound.showcase.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.navigation.KModalNavigationDrawer
import tech.kloos.kompound.navigation.KNavEntry
import tech.kloos.kompound.navigation.KNavItem
import tech.kloos.kompound.navigation.KNavigationBar
import tech.kloos.kompound.navigation.KNavigationDrawer
import tech.kloos.kompound.navigation.KNavigationRail
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

private const val Usage_navigation = """import tech.kloos.kompound.navigation.KNavItem
import tech.kloos.kompound.navigation.KNavigationBar
import tech.kloos.kompound.navigation.KNavigationRail
import tech.kloos.kompound.navigation.KModalNavigationDrawer
import tech.kloos.kompound.navigation.KNavEntry
import tech.kloos.kompound.navigation.KNavigationDrawer

val items = listOf(
    KNavItem("home", "Home", icon = { KIcon(HomeIcon, null) }),
    KNavItem("inbox", "Inbox", icon = { KIcon(InboxIcon, null) }, badge = "4"),
    KNavItem("settings", "Settings", icon = { KIcon(SettingsIcon, null) }),
)
var selected by remember { mutableStateOf("home") }

// Phones: a bottom bar. Tablets and desktop: a rail or a permanent drawer.
KNavigationBar(items, selected, onSelect = { selected = it })
KNavigationRail(items, selected, onSelect = { selected = it }, header = { KIconButton(onClick = { }, "Menu") { KIcon(MenuIcon, null) } })

// A drawer that slides over the screen; put a KNavigationDrawer inside.
KModalNavigationDrawer(open, onDismissRequest = { open = false }, drawer = {
    KNavigationDrawer(items.map { KNavEntry.Item(it) } + KNavEntry.Divider, selected, onSelect = { selected = it; open = false })
}) { Screen() }"""

@KompoundDemo(
    id = "navigation.bar-rail-drawer",
    title = "Navigation bar, rail and drawer",
    description = "Bottom bar, side rail, permanent drawer and modal drawer for switching between top-level destinations.",
    category = KompoundCategory.Navigation,
    tags = ["navigation", "bar", "rail", "drawer", "menu", "destinations", "sidebar"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_navigation,
)
@Composable
fun DemoScope.KNavigationDemo() {
    val kind = choiceControl("Component", listOf("Bar", "Rail", "Drawer", "Modal drawer"))
    val badge = boolControl("Badge", true)
    val labels = boolControl("Always show labels", true)
    val disabled = boolControl("Disable one item", true)
    var selected by remember { mutableStateOf("home") }
    var open by remember { mutableStateOf(false) }
    val items = listOf(
        KNavItem("home", "Home", { KIcon(DemoIcons.Home, null) }),
        KNavItem("inbox", "Inbox", { KIcon(DemoIcons.Inbox, null) }, badge = if (badge) "4" else null),
        KNavItem("people", "People", { KIcon(DemoIcons.Person, null) }, enabled = !disabled),
        KNavItem("settings", "Settings", { KIcon(DemoIcons.Settings, null) }),
    )
    val frame = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (kind) {
            "Bar" -> Column(frame) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { KText("Screen: $selected") }
                KNavigationBar(items, selected, { selected = it }, alwaysShowLabel = labels)
            }
            "Rail" -> Row(frame) {
                KNavigationRail(items, selected, { selected = it }, header = { KIconButton({}, "Menu") { KIcon(DemoIcons.Menu, null) } }, alwaysShowLabel = labels)
                Box(Modifier.weight(1f).fillMaxSize().padding(16.dp)) { KText("Screen: $selected") }
            }
            "Drawer" -> Row(frame) {
                KNavigationDrawer(
                    listOf(KNavEntry.Item(items[0]), KNavEntry.Item(items[1]), KNavEntry.Divider, KNavEntry.Section("Manage"), KNavEntry.Item(items[2]), KNavEntry.Item(items[3])),
                    selected, { selected = it }, Modifier.height(360.dp), header = { KText("My app", Modifier.padding(16.dp)) },
                )
                Box(Modifier.weight(1f).fillMaxSize().padding(16.dp)) { KText("Screen: $selected") }
            }
            else -> KModalNavigationDrawer(
                open, { open = false },
                drawer = { KNavigationDrawer(items.map { KNavEntry.Item(it) }, selected, { selected = it; open = false }, header = { KText("My app", Modifier.padding(16.dp)) }) },
                modifier = frame,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KIconButton({ open = true }, "Open navigation menu") { KIcon(DemoIcons.Menu, null) }
                    KText("Screen: $selected")
                }
            }
        }
        KText("Arrow keys move between destinations; Escape closes the modal drawer.")
    }
}
