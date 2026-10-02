package tech.kloos.kompound.catalog

import androidx.compose.runtime.Immutable
import tech.kloos.kompound.demo.DemoEntry

/** User selected search and filters. Pure data, so it is unit-testable. */
@Immutable
data class CatalogFilter(
    val query: String = "",
    val categories: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val requireAllTags: Boolean = false,
    val platforms: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
)

fun List<DemoEntry>.apply(filter: CatalogFilter): List<DemoEntry> {
    val terms = filter.query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return filter { e ->
        val m = e.meta
        val haystack = buildString {
            append(m.title).append(' ').append(m.description).append(' ').append(m.category).append(' ')
            append(m.tags.joinToString(" "))
        }.lowercase()
        terms.all { it in haystack } &&
            (filter.categories.isEmpty() || m.category in filter.categories) &&
            (filter.statuses.isEmpty() || m.status in filter.statuses) &&
            (filter.platforms.isEmpty() || m.platforms.isEmpty() || m.platforms.any { it in filter.platforms }) &&
            (filter.tags.isEmpty() ||
                if (filter.requireAllTags) m.tags.containsAll(filter.tags) else m.tags.any { it in filter.tags })
    }
}

/** Facet counts for the filter UI, derived from the full list. */
data class Facets(val categories: Map<String, Int>, val tags: Map<String, Int>, val statuses: Map<String, Int>)

fun List<DemoEntry>.facets(): Facets = Facets(
    categories = groupingBy { it.meta.category }.eachCount().sortedByKey(),
    tags = flatMap { it.meta.tags }.groupingBy { it }.eachCount().sortedByKey(),
    statuses = groupingBy { it.meta.status }.eachCount().sortedByKey(),
)

/** Resolves an id (current or alias) used in deep links. */
fun List<DemoEntry>.find(id: String): DemoEntry? =
    firstOrNull { it.qualifiedId == id } ?: firstOrNull { e -> e.meta.aliases.any { "${e.moduleId}/$it" == id } }

private fun Map<String, Int>.sortedByKey(): Map<String, Int> = entries.sortedBy { it.key }.associate { it.key to it.value }
