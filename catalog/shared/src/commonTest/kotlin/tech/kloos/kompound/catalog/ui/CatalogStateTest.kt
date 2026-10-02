package tech.kloos.kompound.catalog.ui

import tech.kloos.kompound.demo.DemoEntry
import tech.kloos.kompound.demo.DemoMeta
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStateTest {
    private fun e(id: String, title: String, category: String, tags: List<String>) =
        DemoEntry("m", DemoMeta(id, title, "desc of $title", category, tags, "0.1.0", "Stable", emptyList(), emptyList())) {}

    private val state = CatalogState(
        listOf(
            e("z", "Zebra", "Display", listOf("animal", "stripes")),
            e("a", "Apple", "Inputs", listOf("fruit")),
            e("b", "Banana", "Display", listOf("fruit", "yellow")),
        ),
    )

    @Test
    fun visibleEntriesAreOrderedByCategoryThenTitle() {
        assertEquals(listOf("Banana", "Zebra", "Apple"), state.visible.map { it.meta.title })
    }

    @Test
    fun togglingCategoriesAndTagsFilters() {
        state.toggleCategory("Display")
        assertEquals(listOf("Banana", "Zebra"), state.visible.map { it.meta.title })
        state.toggleTag("fruit")
        assertEquals(listOf("Banana"), state.visible.map { it.meta.title })
        assertEquals(2, state.activeFilterCount)
        state.toggleTag("fruit")
        state.toggleCategory("Display")
        assertEquals(3, state.visible.size)
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun clearFiltersResetsEverythingIncludingTheQuery() {
        state.filter = state.filter.copy(query = "zebra")
        state.toggleTag("animal")
        state.clearFilters()
        assertEquals(3, state.visible.size)
        assertEquals("", state.filter.query)
    }

    @Test
    fun facetsCoverAllEntries() {
        assertEquals(2, state.facets.categories["Display"])
        assertEquals(2, state.facets.tags["fruit"])
    }

    @Test
    fun selectedFollowsTheSelectedIdAndResolvesAliases() {
        assertEquals(null, state.selected)
        state.selectedId = "m/a"
        assertEquals("Apple", state.selected?.meta?.title)
    }
}
