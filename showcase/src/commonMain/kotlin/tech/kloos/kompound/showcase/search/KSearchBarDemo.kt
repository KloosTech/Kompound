package tech.kloos.kompound.showcase.search

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
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.search.KSearchBar
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

private const val Usage_search_bar = """import tech.kloos.kompound.search.KSearchBar

var query by remember { mutableStateOf("") }

KSearchBar(
    query = query,
    onQueryChange = { query = it },     // fires on every keystroke
    onSearch = { runSearch(it) },       // fires on the keyboard's search action
    placeholder = "Search components",
    modifier = Modifier.fillMaxWidth(),
)"""

@KompoundDemo(
    id = "search.bar",
    title = "KSearchBar",
    description = "Filled search pill with search icon, clear button, keyboard Search action and a slot for filters.",
    category = KompoundCategory.Inputs,
    tags = ["search", "query", "filter", "input"],
    since = "0.1.0",
    usage = Usage_search_bar,
)
@Composable
fun DemoScope.KSearchBarDemo() {
    val enabled = boolControl("Enabled", true)
    val filter = boolControl("Filter button slot", false)
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        KSearchBar(
            query = query, onQueryChange = { query = it }, onSearch = { submitted = it }, enabled = enabled,
            trailingActions = if (filter) ({ KIconButton(onClick = {}, contentDescription = "Filter") { KIcon(DemoIcons.Star, null) } }) else null,
        )
        KText(if (submitted.isEmpty()) "Press search on the keyboard" else "Searched for: $submitted", Modifier.padding(top = 12.dp))
    }
}
