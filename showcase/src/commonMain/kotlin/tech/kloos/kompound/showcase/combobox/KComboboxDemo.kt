package tech.kloos.kompound.showcase.combobox

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
import tech.kloos.kompound.combobox.KCombobox
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_combobox = """import tech.kloos.kompound.combobox.KCombobox

var text by remember { mutableStateOf("") }
var country by remember { mutableStateOf<Country?>(null) }

KCombobox(
    value = text,
    onValueChange = { text = it; country = null },   // typing clears the choice
    options = countries,
    onOptionSelected = { country = it },             // the label is also written into the field
    optionLabel = { it.name },
    optionSupportingText = { it.region },
    label = "Country",
)

// Server-side search: KCombobox(..., options = results, filterOptions = false)"""

private class Country(val name: String, val region: String)

private val Countries = listOf(
    "Austria" to "Europe", "Australia" to "Oceania", "Belgium" to "Europe", "Brazil" to "South America", "Canada" to "North America",
    "Chile" to "South America", "Denmark" to "Europe", "Egypt" to "Africa", "Finland" to "Europe", "France" to "Europe", "Germany" to "Europe",
    "Ghana" to "Africa", "India" to "Asia", "Italy" to "Europe", "Japan" to "Asia", "Kenya" to "Africa", "Mexico" to "North America",
    "New Zealand" to "Oceania", "Norway" to "Europe", "Peru" to "South America", "Portugal" to "Europe", "Spain" to "Europe", "Sweden" to "Europe",
).map { Country(it.first, it.second) }

@KompoundDemo(
    id = "combobox.basic",
    title = "KCombobox",
    description = "A text field with a fuzzy-filtered suggestion list; arrow keys and Enter pick, focus stays in the field.",
    category = KompoundCategory.Inputs,
    tags = ["combobox", "autocomplete", "select", "search", "typeahead", "suggestions"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_combobox,
)
@Composable
fun DemoScope.KComboboxDemo() {
    val enabled = boolControl("Enabled", true)
    val filter = boolControl("Filter options", true)
    val showRegion = boolControl("Show region", true)
    var text by remember { mutableStateOf("") }
    var country by remember { mutableStateOf<Country?>(null) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KCombobox(
            value = text, onValueChange = { text = it; country = null },
            options = Countries, onOptionSelected = { country = it }, optionLabel = { it.name },
            modifier = Modifier.fillMaxWidth(), label = "Country", placeholder = "Type to search", enabled = enabled,
            optionSupportingText = if (showRegion) { { it.region } } else null, filterOptions = filter,
        )
        KText(country?.let { "Chosen: ${it.name} (${it.region})" } ?: "Nothing chosen yet")
    }
}
