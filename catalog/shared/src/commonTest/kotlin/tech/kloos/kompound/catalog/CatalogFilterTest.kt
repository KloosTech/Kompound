package tech.kloos.kompound.catalog

import tech.kloos.kompound.demo.DemoEntry
import tech.kloos.kompound.demo.DemoMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogFilterTest {
    private fun e(id: String, title: String, cat: String, tags: List<String>, aliases: List<String> = emptyList()) =
        DemoEntry("m", DemoMeta(id, title, "desc of $title", cat, tags, "0.1.0", "Stable", emptyList(), aliases)) {}

    private val all = listOf(
        e("button", "Button", "Buttons", listOf("action", "cta")),
        e("field", "Text field", "Inputs", listOf("input", "text")),
        e("chip", "Chip", "Inputs", listOf("action")),
    )

    @Test fun searchMatchesTitleAndTags() {
        assertEquals(listOf("button"), all.apply(CatalogFilter(query = "cta")).map { it.meta.id })
        assertEquals(listOf("field"), all.apply(CatalogFilter(query = "TEXT fie")).map { it.meta.id })
    }

    @Test fun categoryAndTagFilters() {
        assertEquals(2, all.apply(CatalogFilter(categories = setOf("Inputs"))).size)
        assertEquals(listOf("button", "chip"), all.apply(CatalogFilter(tags = setOf("action"))).map { it.meta.id })
        assertTrue(all.apply(CatalogFilter(tags = setOf("action", "cta"), requireAllTags = true)).size == 1)
    }

    @Test fun facetsCount() {
        assertEquals(2, all.facets().categories["Inputs"])
        assertEquals(2, all.facets().tags["action"])
    }

    @Test fun aliasResolvesDeepLink() {
        val list = listOf(e("button.primary", "Button", "Buttons", emptyList(), aliases = listOf("button")))
        assertNotNull(list.find("m/button"))
    }

    @Test fun generatedRegistryIsDiscovered() {
        assertTrue(KompoundAllDemos.entries.any { it.qualifiedId == "showcase/button.primary" })
    }
}
