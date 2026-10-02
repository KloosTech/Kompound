package tech.kloos.kompound.showcase.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.selection.KRadioButton
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "radio.basic",
    title = "KRadioButton",
    description = "Radio button with label; group options in a selectableGroup so screen readers announce them together.",
    category = KompoundCategory.Inputs,
    tags = ["radio", "choice", "form", "select", "option"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KRadioButtonDemo() {
    val enabled = boolControl("Enabled", true)
    var selected by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(16.dp).selectableGroup()) {
        listOf("Small", "Medium", "Large").forEachIndexed { i, text ->
            KRadioButton(selected == i, { selected = i }, enabled = enabled, label = { KText(text) })
        }
    }
}
