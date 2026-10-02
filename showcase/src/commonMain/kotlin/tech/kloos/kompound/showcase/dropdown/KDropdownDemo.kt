package tech.kloos.kompound.showcase.dropdown

import androidx.compose.foundation.layout.Arrangement
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
import tech.kloos.kompound.dropdown.KDropdown
import tech.kloos.kompound.dropdown.KMultiDropdown

private val Fruits = listOf("Apple", "Banana", "Cherry", "Dragon fruit", "Elderberry", "Fig", "Grape")

@KompoundDemo(
    id = "dropdown.single",
    title = "KDropdown",
    description = "Field that opens a menu to choose one option, with label, supporting text and error state.",
    category = KompoundCategory.Inputs,
    tags = ["dropdown", "select", "choice", "picker", "form"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KDropdownDemo() {
    val label = textControl("Label", "Fruit")
    val isError = boolControl("Error", false)
    val enabled = boolControl("Enabled", true)
    var selected by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(16.dp)) {
        KDropdown(
            options = Fruits, selected = selected, onSelect = { selected = it },
            label = label.ifEmpty { null }, placeholder = "Choose a fruit",
            supportingText = if (isError) "Please choose a fruit" else null,
            isError = isError, enabled = enabled,
        )
    }
}

@KompoundDemo(
    id = "dropdown.multi",
    title = "KMultiDropdown",
    description = "Dropdown for choosing several options; the menu stays open and the field summarises the choices.",
    category = KompoundCategory.Inputs,
    tags = ["dropdown", "multi", "select", "choice", "form", "checkbox"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KMultiDropdownDemo() {
    val enabled = boolControl("Enabled", true)
    var selected by remember { mutableStateOf(setOf("Apple", "Cherry")) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KMultiDropdown(
            options = Fruits, selected = selected, onSelectionChange = { selected = it },
            label = "Fruits", placeholder = "Choose fruits", enabled = enabled,
        )
    }
}
