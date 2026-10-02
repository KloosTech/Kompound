package tech.kloos.kompound.showcase.selection

import androidx.compose.foundation.layout.Column
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
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.selection.KSwitch
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "switch.basic",
    title = "KSwitch",
    description = "On/off switch whose thumb and track animate through the Style, with optional label.",
    category = KompoundCategory.Inputs,
    tags = ["switch", "toggle", "on off", "setting", "form"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KSwitchDemo() {
    val enabled = boolControl("Enabled", true)
    val showLabel = boolControl("Label", true)
    var wifi by remember { mutableStateOf(true) }
    var bluetooth by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        KSwitch(wifi, { wifi = it }, enabled = enabled, label = if (showLabel) ({ KText("Wi-Fi") }) else null)
        KSwitch(bluetooth, { bluetooth = it }, enabled = enabled, label = if (showLabel) ({ KText("Bluetooth") }) else null)
    }
}
