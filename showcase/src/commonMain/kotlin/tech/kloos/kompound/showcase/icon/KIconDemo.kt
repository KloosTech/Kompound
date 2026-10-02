package tech.kloos.kompound.showcase.icon

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.theme.KompoundTheme

@KompoundDemo(
    id = "icon.basic",
    title = "KIcon",
    description = "Tinted vector icon with a 24dp default size and a required content description.",
    category = KompoundCategory.Display,
    tags = ["icon", "image", "tint"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KIconDemo() {
    val size = choiceControl("Size", listOf(16, 24, 32, 48), initial = 24, label = { "${it}dp" })
    val tint = choiceControl("Tint", listOf("Default", "Primary", "Error", "Success"))
    val color = when (tint) {
        "Primary" -> MaterialTheme.colorScheme.primary
        "Error" -> MaterialTheme.colorScheme.error
        "Success" -> KompoundTheme.tokens.colors.success
        else -> Color.Unspecified
    }
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        KIcon(DemoIcons.Star, contentDescription = "Star", style = Style { size(size.dp) }, tint = color)
        KIcon(DemoIcons.Check, contentDescription = "Check", style = Style { size(size.dp) }, tint = color)
    }
}
