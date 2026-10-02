package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeEmphasis
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.search.KSearchBar
import tech.kloos.kompound.selection.KSwitch
import tech.kloos.kompound.sheet.KBottomSheet
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.tooltip.KTooltip

/** An icon button with a tooltip and a small count badge while [count] > 0. */
@Composable
internal fun FilterIconButton(icon: ImageVector, description: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier) {
        KTooltip(description) {
            KIconButton(onClick = onClick, contentDescription = description, variant = if (count > 0) KButtonVariant.Tonal else KButtonVariant.Text) {
                KIcon(icon, contentDescription = null)
            }
        }
        if (count > 0) KBadge("$count", Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp), tone = KBadgeTone.Primary)
    }
}

/** Category filter: an icon button that opens a multi-select menu with counts. */
@Composable
internal fun CategoryFilter(state: CatalogState) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterIconButton(CatalogIcons.Category, "Filter by category", state.filter.categories.size) { open = true }
        KMenu(expanded = open, onDismissRequest = { open = false }) {
            state.facets.categories.forEach { (category, count) ->
                KMenuItem(
                    text = "$category  ·  $count",
                    onClick = { state.toggleCategory(category) },
                    selected = category in state.filter.categories, showCheck = true, role = Role.Checkbox,
                )
            }
            if (state.filter.categories.isNotEmpty()) {
                KMenuItem("Clear categories", onClick = { state.filter = state.filter.copy(categories = emptySet()); open = false })
            }
        }
    }
}

/** Tag filter sheet: search the tag list, pick several, choose any/all matching. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagFilterSheet(state: CatalogState, onDismiss: () -> Unit) {
    var tagQuery by remember { mutableStateOf("") }
    val tags = state.facets.tags.filterKeys { tagQuery.isBlank() || tagQuery.trim().lowercase() in it }
    KBottomSheet(
        onDismissRequest = onDismiss,
        title = "Filter by tag",
        showCloseButton = true,
        actions = {
            KButton(onClick = { state.filter = state.filter.copy(tags = emptySet()) }, variant = KButtonVariant.Text, enabled = state.filter.tags.isNotEmpty()) { KText("Clear") }
            KButton(onClick = onDismiss) { KText("Show ${state.visible.size} components") }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KSearchBar(tagQuery, { tagQuery = it }, placeholder = "Search ${state.facets.tags.size} tags")
            KSwitch(
                checked = state.filter.requireAllTags,
                onCheckedChange = { state.filter = state.filter.copy(requireAllTags = it) },
                label = { KText("Match all selected tags") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { (tag, count) ->
                    KChip("$tag  $count", onClick = { state.toggleTag(tag) }, selected = tag in state.filter.tags)
                }
            }
            if (tags.isEmpty()) KText("No tag matches \"$tagQuery\"", style = textRole(quiet = true) { it.bodyMedium })
        }
    }
}

/** Removable chips for every active filter, with a clear-all button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveFilters(state: CatalogState, modifier: Modifier = Modifier) {
    if (state.activeFilterCount == 0) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.filter.categories.sorted().forEach { c ->
                KChip(c, onClick = { state.toggleCategory(c) }, selected = true, trailing = { KIcon(CatalogIcons.Close, "Remove $c", style = Style { size(16.dp) }) })
            }
            state.filter.tags.sorted().forEach { t ->
                KChip("#$t", onClick = { state.toggleTag(t) }, selected = true, trailing = { KIcon(CatalogIcons.Close, "Remove $t", style = Style { size(16.dp) }) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KText("${state.visible.size} of ${state.entries.size} components", Modifier.weight(1f), style = textRole(quiet = true) { it.labelMedium })
            KButton(onClick = { state.clearFilters() }, variant = KButtonVariant.Text) { KText("Clear all") }
        }
    }
}

/** Neutral count badge used in section headers. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    KBadge("$count", modifier, tone = KBadgeTone.Neutral, emphasis = KBadgeEmphasis.Subtle)
}
