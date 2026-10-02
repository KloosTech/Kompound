package tech.kloos.kompound.catalog.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tech.kloos.kompound.catalog.CatalogFilter
import tech.kloos.kompound.catalog.Facets
import tech.kloos.kompound.catalog.apply
import tech.kloos.kompound.catalog.facets
import tech.kloos.kompound.catalog.find
import tech.kloos.kompound.demo.DemoEntry

/** Search, filters and selection of the catalog; plain state so it can be tested without UI. */
@Stable
class CatalogState(val entries: List<DemoEntry>) {
    var filter by mutableStateOf(CatalogFilter())
    var selectedId by mutableStateOf<String?>(null)

    val facets: Facets = entries.facets()
    /** Entries in the order the sidebar shows them: by category, then title. */
    val visible: List<DemoEntry> by derivedStateOf { entries.apply(filter).sortedWith(compareBy({ it.meta.category }, { it.meta.title.lowercase() })) }
    val selected: DemoEntry? get() = selectedId?.let { entries.find(it) }
    val activeFilterCount: Int get() = filter.categories.size + filter.tags.size

    fun toggleCategory(category: String) {
        filter = filter.copy(categories = filter.categories.toggled(category))
    }

    fun toggleTag(tag: String) {
        filter = filter.copy(tags = filter.tags.toggled(tag))
    }

    fun clearFilters() {
        filter = CatalogFilter()
    }

    private fun Set<String>.toggled(value: String) = if (value in this) this - value else this + value
}
