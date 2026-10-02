package tech.kloos.kompound.showcase.buttons

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KFab
import tech.kloos.kompound.buttons.KFabDefaults
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText
import androidx.compose.foundation.style.Style

private const val Usage_button_fab = """import tech.kloos.kompound.buttons.KFab
import tech.kloos.kompound.icon.KIcon

KFab(onClick = { create() }, contentDescription = "New item") {
    KIcon(Icons.Rounded.Add, contentDescription = null)
}"""

@KompoundDemo(
    id = "button.fab",
    title = "KFab",
    description = "Floating action button for the primary action of a screen, regular or extended.",
    category = KompoundCategory.Buttons,
    tags = ["button", "fab", "floating", "action"],
    since = "0.1.0",
    usage = Usage_button_fab,
)
@Composable
fun DemoScope.KFabDemo() {
    val extended = boolControl("Extended", false)
    val enabled = boolControl("Enabled", true)
    KFab(
        onClick = {},
        contentDescription = "Create",
        modifier = Modifier.padding(16.dp),
        style = if (extended) KFabDefaults.extendedStyle() else Style,
        enabled = enabled,
    ) {
        KIcon(DemoIcons.Star, null)
        if (extended) KText("Create", Modifier.padding(start = 8.dp))
    }
}
