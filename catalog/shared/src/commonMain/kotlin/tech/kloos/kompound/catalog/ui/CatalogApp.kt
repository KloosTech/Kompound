package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.catalog.CatalogFilter
import tech.kloos.kompound.catalog.KompoundAllDemos
import tech.kloos.kompound.catalog.apply
import tech.kloos.kompound.catalog.facets
import tech.kloos.kompound.catalog.find
import tech.kloos.kompound.demo.DemoEntry
import tech.kloos.kompound.demo.DemoScope

private val WideBreakpoint = 720.dp

/** Entry point for every launcher (android, ios, desktop, web): themed catalog. */
@Composable
fun KompoundCatalog() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
        CatalogApp()
    }
}

/** Catalog content: searchable, filterable list plus live demo. Adaptive: two panes when wide. */
@Composable
fun CatalogApp(entries: List<DemoEntry> = KompoundAllDemos.entries) {
    var filter by remember { mutableStateOf(CatalogFilter()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val facets = remember(entries) { entries.facets() }
    val visible = remember(entries, filter) { entries.apply(filter) }
    val selected = selectedId?.let { entries.find(it) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= WideBreakpoint
            val list = @Composable { modifier: Modifier ->
                ListPane(visible, facets.categories.keys, facets.tags.keys, filter, { filter = it }, selected?.qualifiedId,
                    { selectedId = it.qualifiedId }, modifier)
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.width(360.dp).fillMaxSize())
                    DetailPane(selected, onBack = null, Modifier.fillMaxSize())
                }
            } else if (selected != null) {
                DetailPane(selected, onBack = { selectedId = null }, Modifier.fillMaxSize())
            } else {
                list(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ListPane(
    entries: List<DemoEntry>,
    categories: Set<String>,
    tags: Set<String>,
    filter: CatalogFilter,
    onFilter: (CatalogFilter) -> Unit,
    selectedId: String?,
    onSelect: (DemoEntry) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilter(filter.copy(query = it)) },
            label = { Text("Search components") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ChipRow("Category", categories, filter.categories) { onFilter(filter.copy(categories = it)) }
        ChipRow("Tag", tags, filter.tags) { onFilter(filter.copy(tags = it)) }
        Text("${entries.size} components", style = MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.qualifiedId }) { e ->
                Column(
                    Modifier.fillMaxWidth().clickable { onSelect(e) }.padding(vertical = 10.dp),
                ) {
                    Text(e.meta.title, style = MaterialTheme.typography.titleMedium,
                        color = if (e.qualifiedId == selectedId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(e.meta.description, style = MaterialTheme.typography.bodySmall)
                    Text(e.meta.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, options: Set<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    if (options.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
        options.forEach { o ->
            FilterChip(
                selected = o in selected,
                onClick = { onChange(if (o in selected) selected - o else selected + o) },
                label = { Text(o) },
            )
        }
    }
}

private object Scope : DemoScope

@Composable
private fun DetailPane(entry: DemoEntry?, onBack: (() -> Unit)?, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) TextButton(onClick = onBack) { Text("‹ Back") }
        if (entry == null) {
            Box(Modifier.fillMaxSize()) { Text("Select a component", Modifier.padding(16.dp)) }
            return@Column
        }
        Text(entry.meta.title, style = MaterialTheme.typography.headlineSmall)
        Text(entry.meta.description)
        Text("${entry.meta.category} · ${entry.meta.status} · since ${entry.meta.since.ifEmpty { "-" }}",
            style = MaterialTheme.typography.labelMedium)
        HorizontalDivider()
        val content = entry.content
        Box(Modifier.fillMaxWidth()) { Scope.content() }
    }
}
