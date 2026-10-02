package tech.kloos.kompound.showcase.surface

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.surface.KSurface
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "surface.basic",
    title = "KSurface",
    description = "Container with background, shape and content colour; optionally clickable with state layers.",
    category = KompoundCategory.Layout,
    tags = ["surface", "container", "card", "background"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KSurfaceDemo() {
    val clickable = boolControl("Clickable", true)
    val enabled = boolControl("Enabled", true)
    val padding = floatControl("Padding", 0f..48f, 16f)
    val tone = choiceControl("Content colour", listOf("Default", "Primary", "Error"))
    val color = when (tone) {
        "Primary" -> MaterialTheme.colorScheme.primary
        "Error" -> MaterialTheme.colorScheme.error
        else -> Color.Unspecified
    }
    val container = MaterialTheme.colorScheme.surfaceContainerHigh   // visible against the catalog background
    val style = Style { background(container); contentPadding(padding.dp) }
    val content: @Composable () -> Unit = {
        Column {
            KIcon(DemoIcons.Star, contentDescription = null)
            KText("Text and icon share the content colour")
        }
    }
    Column(Modifier.padding(16.dp)) {
        if (clickable) {
            KSurface(onClick = {}, enabled = enabled, role = Role.Button, style = style, contentColor = color, content = content)
        } else {
            KSurface(style = style, contentColor = color, content = content)
        }
    }
}
