package tech.kloos.kompound.showcase.slider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.slider.KSlider
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "slider.basic",
    title = "KSlider",
    description = "Slider for picking a number by drag, tap or keyboard, continuous or in steps, mirrored in RTL.",
    category = KompoundCategory.Inputs,
    tags = ["slider", "range", "number", "input", "drag"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KSliderDemo() {
    val enabled = boolControl("Enabled", true)
    val steps = floatControl("Steps (0 = continuous)", 0f..10f, 0f).toInt()
    var value by remember { mutableFloatStateOf(40f) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KText("Value: ${value.toInt()}")
        KSlider(value, { value = it }, valueRange = 0f..100f, steps = steps, enabled = enabled)
    }
}
