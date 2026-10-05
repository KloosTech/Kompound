package tech.kloos.kompound.showcase.color

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.color.KColorField
import tech.kloos.kompound.color.KColorPicker
import tech.kloos.kompound.color.KColors
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_color = """import tech.kloos.kompound.color.KColorField
import tech.kloos.kompound.color.KColorPicker
import tech.kloos.kompound.color.KColors

var color by remember { mutableStateOf(Color(0xFF6750A4)) }
KColorPicker(color, { color = it }, showAlpha = true, swatches = listOf(Color.Red, Color.Green, Color.Blue))

// Hex helpers: KColors.toHex(color) and KColors.parseHex("#336699")

// As a form field that opens a dialog:
KColorField(color, { color = it }, label = "Accent")"""

private val Swatches = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF6750A4, 0xFF8E24AA, 0xFF212121).map { Color(it) }

@KompoundDemo(
    id = "color.picker",
    title = "KColorPicker",
    description = "A saturation square, hue and opacity sliders, a hex field and swatches, all keyboard operable and announced.",
    category = KompoundCategory.Inputs,
    tags = ["color", "colour", "picker", "hue", "hex", "swatch", "opacity"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_color,
)
@Composable
fun DemoScope.KColorPickerDemo() {
    val enabled = boolControl("Enabled", true)
    val alpha = boolControl("Opacity", false)
    val hex = boolControl("Hex field", true)
    val swatches = boolControl("Swatches", true)
    var color by remember { mutableStateOf(Color(0xFF6750A4)) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KColorPicker(color, { color = it }, Modifier.fillMaxWidth(), showAlpha = alpha, showHex = hex, swatches = if (swatches) Swatches else emptyList(), enabled = enabled)
        KText("Picked: ${KColors.toHex(color, alpha)}")
        KColorField(color, { color = it }, Modifier.fillMaxWidth(), label = "Accent colour", enabled = enabled, showAlpha = alpha)
    }
}
