package tech.kloos.kompound.showcase.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons

private const val Usage_button_icon = """import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon

// contentDescription is required: an icon button has no visible label.
KIconButton(onClick = { openSettings() }, contentDescription = "Settings") {
    KIcon(Icons.Rounded.Settings, contentDescription = null)
}

KIconButton(onClick = { openSettings() }, contentDescription = "Settings", variant = KButtonVariant.Tonal) {
    KIcon(Icons.Rounded.Settings, contentDescription = null)
}"""

@KompoundDemo(
    id = "button.icon",
    title = "KIconButton",
    description = "Round icon button with a required content description and a 48dp touch target.",
    category = KompoundCategory.Buttons,
    tags = ["button", "icon", "action"],
    since = "0.1.0",
    usage = Usage_button_icon,
)
@Composable
fun DemoScope.KIconButtonDemo() {
    val variant = choiceControl("Variant", KButtonVariant.entries, initial = KButtonVariant.Text)
    val enabled = boolControl("Enabled", true)
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KIconButton(onClick = {}, contentDescription = "Favourite", variant = variant, enabled = enabled) { KIcon(DemoIcons.Star, null) }
        KIconButton(onClick = {}, contentDescription = "Confirm", variant = variant, enabled = enabled) { KIcon(DemoIcons.Check, null) }
    }
}
