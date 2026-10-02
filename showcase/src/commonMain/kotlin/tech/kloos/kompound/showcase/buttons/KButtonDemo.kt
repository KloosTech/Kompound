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
import tech.kloos.kompound.buttons.KButtonEffects
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

private const val Usage_button_primary = """import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonEffects
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.text.KText

// The simplest button: the content slot takes KText and KIcon.
KButton(onClick = { save() }) {
    KText("Save")
}

// Other looks, a spinner while work runs, and optional press effects.
KButton(
    onClick = { save() },
    variant = KButtonVariant.Tonal,   // Filled, Tonal, Outlined or Text
    loading = isSaving,               // spinner instead of the content; the size stays, clicks are ignored
    effects = KButtonEffects(bounce = true, sparkles = true),
) {
    KIcon(Icons.Rounded.Star, contentDescription = null)
    KText("Save", Modifier.padding(start = 8.dp))
}"""

@KompoundDemo(
    id = "button.primary",
    title = "KButton",
    description = "Filled, tonal, outlined and text buttons with optional press effects: click shadow, bounce, fade, colour and shape morph, sparkles.",
    category = KompoundCategory.Buttons,
    tags = ["button", "action", "cta", "styles", "outlined", "tonal", "animation", "bounce", "sparkles", "shadow", "morph"],
    since = "0.1.0",
    usage = Usage_button_primary,
)
@Composable
fun DemoScope.KButtonDemo() {
    val variant = choiceControl("Variant", KButtonVariant.entries)
    val label = textControl("Label", "Button")
    val enabled = boolControl("Enabled", true)
    val loading = boolControl("Loading", false)
    val leadingIcon = boolControl("Leading icon", false)
    val trailingIcon = boolControl("Trailing icon", false)
    val effects = KButtonEffects(
        clickShadow = boolControl("Click shadow", true),
        bounce = boolControl("Bounce", false),
        fade = boolControl("Fade", false),
        colorMorph = boolControl("Colour morph", false),
        shapeMorph = boolControl("Shape morph", false),
        sparkles = boolControl("Sparkles", false),
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KButton(onClick = {}, variant = variant, enabled = enabled, loading = loading, effects = effects) {
            if (leadingIcon) KIcon(DemoIcons.Star, contentDescription = null)
            KText(label, Modifier.padding(horizontal = if (leadingIcon || trailingIcon) 8.dp else 0.dp))
            if (trailingIcon) KIcon(DemoIcons.Check, contentDescription = null)
        }
        KText("All variants")
        KButtonVariant.entries.forEach { v -> KButton(onClick = {}, variant = v, enabled = enabled, loading = loading, effects = effects) { KText(v.name) } }
    }
}
