package tech.kloos.kompound.showcase.tooltip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.tooltip.KTooltip
import tech.kloos.kompound.tooltip.KTooltipPlacement

@KompoundDemo(
    id = "tooltip.basic",
    title = "KTooltip",
    description = "Small label that appears on hover after a delay (desktop, web) or on long press (touch).",
    category = KompoundCategory.Overlays,
    tags = ["tooltip", "hint", "hover", "popup", "label"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KTooltipDemo() {
    val text = textControl("Text", "Add to favourites")
    val placement = choiceControl("Placement", KTooltipPlacement.entries)
    val enabled = boolControl("Enabled", true)
    val delay = floatControl("Hover delay (ms)", 0f..1500f, 500f)
    Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        KTooltip(text, enabled = enabled, placement = placement, showDelayMillis = delay.toLong()) {
            KIconButton(onClick = {}, contentDescription = text) { KIcon(DemoIcons.Star, null) }
        }
        KTooltip("Saves your work", enabled = enabled, placement = placement, showDelayMillis = delay.toLong()) {
            KButton(onClick = {}, variant = KButtonVariant.Tonal) { KText("Save") }
        }
    }
}
