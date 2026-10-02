package tech.kloos.kompound.showcase.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "button.primary",
    title = "KButton",
    description = "Filled, tonal, outlined and text buttons styled through the Compose Styles API.",
    category = KompoundCategory.Buttons,
    tags = ["button", "action", "cta", "styles", "outlined", "tonal"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KButtonDemo() {
    val variant = choiceControl("Variant", KButtonVariant.entries)
    val label = textControl("Label", "Button")
    val enabled = boolControl("Enabled", true)
    val loading = boolControl("Loading", false)
    val leadingIcon = boolControl("Leading icon", false)
    val trailingIcon = boolControl("Trailing icon", false)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KButton(onClick = {}, variant = variant, enabled = enabled, loading = loading) {
            if (leadingIcon) KIcon(DemoIcons.Star, contentDescription = null)
            KText(label, Modifier.padding(horizontal = if (leadingIcon || trailingIcon) 8.dp else 0.dp))
            if (trailingIcon) KIcon(DemoIcons.Check, contentDescription = null)
        }
        KText("All variants")
        KButtonVariant.entries.forEach { v -> KButton(onClick = {}, variant = v, enabled = enabled, loading = loading) { KText(v.name) } }
    }
}
