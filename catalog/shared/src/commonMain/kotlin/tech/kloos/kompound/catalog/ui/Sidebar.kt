package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeEmphasis
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.demo.DemoEntry
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.search.KSearchBar
import tech.kloos.kompound.state.KEmptyState
import tech.kloos.kompound.text.KText

/** The brand mark: a rounded square in the theme's primary colour with a "K". */
@Composable
internal fun Logo(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val label = remember(c, type) { Style { contentColor(c.onPrimary); textStyle(type.titleMedium.copy(color = c.onPrimary, fontWeight = FontWeight.Bold)) } }
    Box(modifier.size(36.dp).clip(MaterialTheme.shapes.medium).background(c.primary), contentAlignment = Alignment.Center) { KText("K", style = label) }
}

/** Search, icon-button filters, active filter chips and the component list grouped by category. */
@Composable
internal fun Sidebar(
    state: CatalogState,
    onSelect: (DemoEntry) -> Unit,
    onOpenTags: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val grouped = state.visible.groupBy { it.meta.category }   // visible is already sorted by category then title, and groupBy keeps that order
    Column(modifier.fillMaxSize()) {
        if (showHeader) Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo()
            Column(Modifier.weight(1f)) {
                KText("Kompound", style = textRole(weight = FontWeight.SemiBold) { it.titleLarge })
                KText("Component catalog", style = textRole(quiet = true) { it.labelMedium })
            }
            KBadge("alpha", tone = KBadgeTone.Info, emphasis = KBadgeEmphasis.Subtle)
        }
        KSearchBar(state.filter.query, { state.filter = state.filter.copy(query = it) }, Modifier.padding(start = 12.dp, end = 12.dp, top = if (showHeader) 0.dp else 12.dp), placeholder = "Search components")
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            KText("${state.visible.size} components", Modifier.weight(1f), style = textRole(quiet = true) { it.labelMedium })
            CategoryFilter(state)
            FilterIconButton(CatalogIcons.Tag, "Filter by tag", state.filter.tags.size, onClick = onOpenTags)
        }
        ActiveFilters(state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (state.visible.isEmpty()) {
            KEmptyState(
                title = "No components match", description = "Try a different search or remove a filter.",
                modifier = Modifier.padding(top = 24.dp),
                action = { KButton(onClick = { state.clearFilters(); state.filter = state.filter.copy(query = "") }) { KText("Clear search and filters") } },
            )
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                grouped.forEach { (category, list) ->
                    item(key = "header-$category") {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            KText(category.uppercase(), Modifier.weight(1f), style = textRole(quiet = true, weight = FontWeight.SemiBold) { it.labelSmall })
                            CountBadge(list.size)
                        }
                    }
                    items(list, key = { it.qualifiedId }) { entry ->
                        val statusTone = statusTone(entry.meta.status)
                        KListItem(
                            headline = { KText(entry.meta.title, maxLines = 1) },
                            supporting = { KText(entry.meta.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = entry.qualifiedId == state.selectedId,
                            onClick = { onSelect(entry) },
                            trailing = if (entry.meta.status != "Stable") ({ KBadge(entry.meta.status, tone = statusTone, emphasis = KBadgeEmphasis.Subtle) }) else null,
                            style = Style { contentPadding(horizontal = 16.dp, vertical = 8.dp) },
                        )
                    }
                }
            }
        }
    }
}

internal fun statusTone(status: String): KBadgeTone = when (status) {
    "Stable" -> KBadgeTone.Success
    "Beta" -> KBadgeTone.Info
    "Experimental" -> KBadgeTone.Warning
    "Deprecated" -> KBadgeTone.Error
    else -> KBadgeTone.Neutral
}
