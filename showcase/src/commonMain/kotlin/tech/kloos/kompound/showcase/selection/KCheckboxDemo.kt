package tech.kloos.kompound.showcase.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.selection.KCheckbox
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "checkbox.basic",
    title = "KCheckbox",
    description = "Checkbox with label, indeterminate state, 48dp touch target and state-layer halo.",
    category = KompoundCategory.Inputs,
    tags = ["checkbox", "check", "form", "select", "tri-state"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KCheckboxDemo() {
    val enabled = boolControl("Enabled", true)
    val showLabel = boolControl("Label", true)
    var checked by remember { mutableStateOf(true) }
    var parent by remember { mutableStateOf(ToggleableState.Indeterminate) }
    Column(Modifier.padding(16.dp)) {
        KCheckbox(checked, { checked = it }, enabled = enabled, label = if (showLabel) ({ KText("Receive updates") }) else null)
        KCheckbox(
            state = parent,
            onClick = { parent = if (parent == ToggleableState.On) ToggleableState.Off else ToggleableState.On },
            enabled = enabled,
            label = if (showLabel) ({ KText("Select all (tri-state)") }) else null,
        )
    }
}
