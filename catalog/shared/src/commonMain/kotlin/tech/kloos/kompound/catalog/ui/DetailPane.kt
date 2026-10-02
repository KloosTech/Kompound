package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.code.KCode
import tech.kloos.kompound.badge.KBadgeEmphasis
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.demo.DemoControls
import tech.kloos.kompound.demo.DemoEntry
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.state.KEmptyState
import tech.kloos.kompound.surface.KSurface
import tech.kloos.kompound.text.KText

/** Backgrounds the preview can be shown on, to judge a component on light, tinted and inverse surfaces. */
internal enum class Stage { Surface, Page, Tinted, Inverse }

/** Tabs of the detail pane; "How to use" only exists for demos that provide a usage sample. */
private enum class DetailTab(val label: String) { Preview("Preview"), Usage("How to use") }

/** Header, live preview and controls of one component. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailPane(entry: DemoEntry?, componentCount: Int, modifier: Modifier = Modifier, onCopy: (String) -> Unit = {}) {
    if (entry == null) {
        KEmptyState(
            title = "Select a component",
            description = "Choose one of the $componentCount components to see it live and try its states.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val controls = remember(entry.qualifiedId) { DemoControls() }
    var stage by remember(entry.qualifiedId) { mutableStateOf(Stage.Surface) }
    var tab by remember(entry.qualifiedId) { mutableStateOf(DetailTab.Preview) }
    val hasUsage = entry.meta.usage.isNotBlank()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 980.dp
        val compact = maxWidth < 600.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp)) {
            Column(Modifier.widthIn(max = 1200.dp).fillMaxWidth().align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Header(entry)
                if (hasUsage) {
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        KSegmentedControl(DetailTab.entries.map { it.label }, tab.ordinal, { tab = DetailTab.entries[it] })
                    }
                }
                if (tab == DetailTab.Usage && hasUsage) {
                    UsageCard(entry.meta.usage, onCopy)
                } else if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                        PreviewCard(entry, controls, stage, { stage = it }, Modifier.weight(1f), compact = false)
                        ControlsCard(controls, Modifier.widthIn(min = 320.dp, max = 360.dp))
                    }
                } else {
                    PreviewCard(entry, controls, stage, { stage = it }, compact = compact)
                    ControlsCard(controls)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(entry: DemoEntry) {
    val meta = entry.meta
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KBadge(meta.category, tone = KBadgeTone.Primary, emphasis = KBadgeEmphasis.Subtle)
            KBadge(meta.status, tone = statusTone(meta.status), emphasis = KBadgeEmphasis.Subtle)
            if (meta.since.isNotEmpty()) KBadge("since ${meta.since}", tone = KBadgeTone.Neutral, emphasis = KBadgeEmphasis.Subtle)
        }
        KText(meta.title, style = textRole(weight = FontWeight.SemiBold) { it.headlineLarge })
        KText(meta.description, style = textRole(quiet = true) { it.bodyLarge })
        if (meta.tags.isNotEmpty()) {
            KText(meta.tags.joinToString("   ") { "#$it" }, style = textRole(quiet = true) { it.labelMedium })
        }
    }
}

@Composable
private fun PreviewCard(entry: DemoEntry, controls: DemoControls, stage: Stage, onStage: (Stage) -> Unit, modifier: Modifier = Modifier, compact: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val title = @Composable { m: Modifier -> KText("Preview", m, style = textRole(weight = FontWeight.SemiBold) { it.titleMedium }) }
        val selector = @Composable {
            KSegmentedControl(Stage.entries.map { it.name }, stage.ordinal, { onStage(Stage.entries[it]) })
        }
        if (compact) {
            title(Modifier)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { selector() }   // never squeezes the labels
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                title(Modifier.weight(1f))
                selector()
            }
        }
        val content = entry.content
        KSurface(Modifier.fillMaxWidth(), style = stageStyle(stage)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 240.dp), verticalArrangement = Arrangement.Center) { controls.content() }
        }
    }
}

@Composable
private fun UsageCard(usage: String, onCopy: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                KText("How to use", style = textRole(weight = FontWeight.SemiBold) { it.titleMedium })
                KText("Copy it into a composable inside KompoundTheme; your IDE adds the remaining imports.", style = textRole(quiet = true) { it.bodyMedium })
            }
            KButton(onClick = { onCopy(usage) }, variant = KButtonVariant.Tonal) { KText("Copy") }
        }
        KCode(usage, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ControlsCard(controls: DemoControls, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KText("Controls", style = textRole(weight = FontWeight.SemiBold) { it.titleMedium })
            if (controls.controls.isNotEmpty()) KBadge("${controls.controls.size}", tone = KBadgeTone.Neutral, emphasis = KBadgeEmphasis.Subtle)
        }
        KSurface(Modifier.fillMaxWidth(), style = cardStyle()) { ControlPanel(controls.controls) }
    }
}

@Composable
private fun cardStyle(): Style {
    val c = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    return remember(c, shapes) {
        Style { background(c.surfaceContainerLow); contentColor(c.onSurface); shape(shapes.large); borderWidth(1.dp); borderColor(c.outlineVariant); contentPadding(20.dp) }
    }
}

@Composable
private fun stageStyle(stage: Stage): Style {
    val c = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val (fill, ink) = when (stage) {
        Stage.Surface -> c.surface to c.onSurface
        Stage.Page -> c.surfaceContainerHighest to c.onSurface
        Stage.Tinted -> c.primaryContainer to c.onPrimaryContainer
        Stage.Inverse -> c.inverseSurface to c.inverseOnSurface
    }
    return remember(c, shapes, stage) {
        Style { background(fill); contentColor(ink); shape(shapes.large); borderWidth(1.dp); borderColor(c.outlineVariant); contentPadding(24.dp) }
    }
}
