package tech.kloos.kompound.showcase.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.tab.KTab
import tech.kloos.kompound.tab.KTabRow
import tech.kloos.kompound.text.KText

private const val Usage_tab_row = """import tech.kloos.kompound.tab.KTab
import tech.kloos.kompound.tab.KTabRow

var selected by remember { mutableIntStateOf(0) }

Column {
    KTabRow(
        tabs = listOf(KTab("Overview"), KTab("Activity", badge = "3"), KTab("Settings", enabled = false)),
        selectedIndex = selected,
        onSelectedIndexChange = { selected = it },
    )
    // The row only switches; show the selected tab's content yourself.
    when (selected) {
        0 -> Overview()
        1 -> Activity()
    }
}"""

@KompoundDemo(
    id = "tab.row",
    title = "KTabRow",
    description = "A row of tabs with a sliding underline, icons, badges, keyboard arrows and a scrollable variant.",
    category = KompoundCategory.Navigation,
    tags = ["tabs", "tab", "navigation", "underline", "scrollable"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_tab_row,
)
@Composable
fun DemoScope.KTabRowDemo() {
    val scrollable = boolControl("Scrollable", false)
    val icons = boolControl("Icons", false)
    val badge = boolControl("Badge", true)
    val disabled = boolControl("Disable one tab", true)
    val many = boolControl("Many tabs", false)
    var selected by remember { mutableIntStateOf(0) }
    val names = if (many) List(14) { "Section ${it + 1}" } else listOf("Overview", "Activity", "Settings", "Billing")
    val tabs = names.mapIndexed { i, name ->
        KTab(
            name,
            icon = if (icons) ({ KIcon(DemoIcons.Star, null) }) else null,
            badge = if (badge && i == 1) "3" else null,
            enabled = !(disabled && i == 2),
        )
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KTabRow(tabs, selected.coerceAtMost(tabs.size - 1), { selected = it }, Modifier.fillMaxWidth(), scrollable = scrollable || many)
        KText("Content of \"${names[selected.coerceAtMost(names.size - 1)]}\". Use the arrow keys, Home and End when a tab has focus.")
    }
}
