package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.demo.BoolControl
import tech.kloos.kompound.demo.ChoiceControl
import tech.kloos.kompound.demo.DemoControl
import tech.kloos.kompound.demo.FloatControl
import tech.kloos.kompound.demo.TextControl

/** Renders the controls a demo declared through `DemoScope`, so visitors can try a component's states. */
@Composable
fun ControlPanel(controls: List<DemoControl>, modifier: Modifier = Modifier) {
    if (controls.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Controls", style = MaterialTheme.typography.titleSmall)
        controls.forEach { control ->
            when (control) {
                is TextControl -> OutlinedTextField(
                    value = control.value,
                    onValueChange = { control.value = it },
                    label = { Text(control.name) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                is BoolControl -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(control.name, Modifier.weight(1f))
                    Switch(checked = control.value, onCheckedChange = { control.value = it })
                }
                is ChoiceControl -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(control.name, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        control.options.forEachIndexed { i, label ->
                            FilterChip(selected = i == control.selectedIndex, onClick = { control.selectedIndex = i }, label = { Text(label) })
                        }
                    }
                }
                is FloatControl -> Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(control.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text(((control.value * 10).toInt() / 10f).toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(value = control.value, onValueChange = { control.value = it }, valueRange = control.range, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}
