package tech.kloos.kompound.showcase.buttons

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
import tech.kloos.kompound.buttons.KToggleButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_button_toggle = """import tech.kloos.kompound.buttons.KToggleButton
import tech.kloos.kompound.text.KText

var bookmarked by remember { mutableStateOf(false) }

KToggleButton(checked = bookmarked, onCheckedChange = { bookmarked = it }) {
    KText(if (bookmarked) "Bookmarked" else "Bookmark")
}"""

@KompoundDemo(
    id = "button.toggle",
    title = "KToggleButton",
    description = "Button that stays pressed: outlined when off, filled when on.",
    category = KompoundCategory.Buttons,
    tags = ["button", "toggle", "switch", "state"],
    since = "0.1.0",
    usage = Usage_button_toggle,
)
@Composable
fun DemoScope.KToggleButtonDemo() {
    val enabled = boolControl("Enabled", true)
    var checked by remember { mutableStateOf(false) }
    KToggleButton(checked = checked, onCheckedChange = { checked = it }, Modifier.padding(16.dp), enabled = enabled) {
        KText(if (checked) "On" else "Off")
    }
}
