package tech.kloos.kompound.showcase.menu

import androidx.compose.foundation.layout.Box
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
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

private const val Usage_menu_basic = """import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.text.KText

var expanded by remember { mutableStateOf(false) }

// The menu opens relative to its parent, so put both in one Box.
Box {
    KButton(onClick = { expanded = true }) { KText("Actions") }
    KMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        KMenuItem("Rename", onClick = { rename(); expanded = false })
        KMenuItem("Delete", onClick = { delete(); expanded = false })
    }
}"""

@KompoundDemo(
    id = "menu.basic",
    title = "KMenu",
    description = "Popup list of actions that opens below its anchor, flips above when needed and supports arrow keys.",
    category = KompoundCategory.Overlays,
    tags = ["menu", "popup", "dropdown", "actions", "context"],
    since = "0.1.0",
    usage = Usage_menu_basic,
)
@Composable
fun DemoScope.KMenuDemo() {
    val icons = boolControl("Leading icons", true)
    val disabledItem = boolControl("Disable last item", false)
    var expanded by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf("Nothing picked yet") }
    Box(Modifier.padding(16.dp)) {
        KButton(onClick = { expanded = true }, variant = KButtonVariant.Tonal) { KText(last) }
        KMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf("Edit", "Duplicate", "Delete").forEachIndexed { i, text ->
                KMenuItem(
                    text = text,
                    onClick = { last = text; expanded = false },
                    enabled = !(disabledItem && i == 2),
                    leading = if (icons) ({ KIcon(DemoIcons.Star, null) }) else null,
                )
            }
        }
    }
}
