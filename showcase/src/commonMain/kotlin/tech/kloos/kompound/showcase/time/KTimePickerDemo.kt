package tech.kloos.kompound.showcase.time

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.time.KTime
import tech.kloos.kompound.time.KTimeField
import tech.kloos.kompound.time.KTimePicker
import tech.kloos.kompound.time.format

private const val Usage_time = """import tech.kloos.kompound.time.KTime
import tech.kloos.kompound.time.KTimeField
import tech.kloos.kompound.time.KTimePicker

var time by remember { mutableStateOf(KTime(9, 30)) }
KTimePicker(time, { time = it }, is24Hour = false, minuteStep = 5)

// As a form field that opens a dialog:
var start by remember { mutableStateOf<KTime?>(null) }
KTimeField(start, { start = it }, label = "Starts at")"""

@KompoundDemo(
    id = "time.picker",
    title = "KTimePicker",
    description = "Hour and minute spinners with arrow buttons, keys and typed digits, a 12 or 24 hour clock and a field that opens it in a dialog.",
    category = KompoundCategory.Inputs,
    tags = ["time", "clock", "picker", "hour", "minute", "am pm"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_time,
)
@Composable
fun DemoScope.KTimePickerDemo() {
    val enabled = boolControl("Enabled", true)
    val is24 = boolControl("24 hour clock", true)
    val step = floatControl("Minute step", 1f..30f, 1f).toInt()
    var time by remember { mutableStateOf(KTime(9, 30)) }
    var start by remember { mutableStateOf<KTime?>(null) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KTimePicker(time, { time = it }, is24Hour = is24, minuteStep = step, enabled = enabled)
        KText("Picked: ${time.format(is24)}")
        KTimeField(start, { start = it }, Modifier.fillMaxWidth(), label = "Starts at", placeholder = "Choose a time", enabled = enabled, is24Hour = is24, minuteStep = step)
    }
}
