package tech.kloos.kompound.showcase.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import tech.kloos.kompound.text.KText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.demo.DemoScope

@KompoundDemo(
    id = "button.primary",
    title = "KButton",
    description = "Primary action button styled through the Compose Styles API.",
    category = KompoundCategory.Buttons,
    tags = ["button", "action", "cta", "styles"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KButtonDemo() {
    val label = textControl("Label", "Default")
    val enabled = boolControl("Enabled", true)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KButton(onClick = {}, enabled = enabled) { Text(label) }
        KButton(onClick = {}, enabled = false) { Text("Always disabled") }
    }
}
