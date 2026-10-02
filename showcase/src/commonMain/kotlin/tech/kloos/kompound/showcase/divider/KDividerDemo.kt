package tech.kloos.kompound.showcase.divider

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.text.KText

private const val Usage_divider_basic = """import androidx.compose.foundation.gestures.Orientation
import tech.kloos.kompound.divider.KDivider

KDivider()

// A vertical divider needs a height from its parent.
Row(Modifier.height(24.dp)) {
    KText("One")
    KDivider(orientation = Orientation.Vertical)
    KText("Two")
}"""

@KompoundDemo(
    id = "divider.basic",
    title = "KDivider",
    description = "Horizontal or vertical separator line; thickness and colour come from its style.",
    category = KompoundCategory.Layout,
    tags = ["divider", "separator", "line"],
    since = "0.1.0",
    usage = Usage_divider_basic,
)
@Composable
fun DemoScope.KDividerDemo() {
    val vertical = boolControl("Vertical", false)
    val thickness = floatControl("Thickness", 1f..8f, 1f)
    val style = Style { if (vertical) width(thickness.dp) else height(thickness.dp) }
    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (vertical) {
            Row(Modifier.height(48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KText("Left"); KDivider(style = style, orientation = Orientation.Vertical); KText("Right")
            }
        } else {
            KText("Above"); KDivider(style = style); KText("Below")
        }
    }
}
