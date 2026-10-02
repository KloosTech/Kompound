package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.demo.BoolControl
import tech.kloos.kompound.demo.ChoiceControl
import tech.kloos.kompound.demo.DemoControl
import tech.kloos.kompound.demo.FloatControl
import tech.kloos.kompound.demo.TextControl
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.selection.KSwitch
import tech.kloos.kompound.slider.KSlider
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField

/** The controls a demo declared, drawn with Kompound components so visitors can try a component's states. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControlPanel(controls: List<DemoControl>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (controls.isEmpty()) {
            KText("This demo has no controls.", style = textRole(quiet = true) { it.bodyMedium })
        }
        controls.forEach { control ->
            when (control) {
                is TextControl -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ControlLabel(control.name)
                    KTextField(control.value, { control.value = it })
                }
                is BoolControl -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ControlLabel(control.name, Modifier.weight(1f))
                    KSwitch(control.value, { control.value = it })
                }
                is ChoiceControl -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ControlLabel(control.name)
                    if (control.options.size <= 3 && control.options.all { it.length <= 12 }) {
                        KSegmentedControl(control.options, control.selectedIndex, { control.selectedIndex = it })
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            control.options.forEachIndexed { i, label ->
                                KChip(label, onClick = { control.selectedIndex = i }, selected = i == control.selectedIndex)
                            }
                        }
                    }
                }
                is FloatControl -> Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ControlLabel(control.name, Modifier.weight(1f))
                        KText(formatNumber(control.value), style = textRole(quiet = true) { it.labelMedium })
                    }
                    KSlider(control.value, { control.value = it }, valueRange = control.range)
                }
            }
        }
    }
}

@Composable
private fun ControlLabel(text: String, modifier: Modifier = Modifier) {
    KText(text, modifier, style = textRole { it.labelLarge })
}

internal fun formatNumber(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else ((v * 100).toInt() / 100f).toString()
