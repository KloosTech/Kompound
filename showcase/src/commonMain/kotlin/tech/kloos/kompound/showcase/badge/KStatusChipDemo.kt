package tech.kloos.kompound.showcase.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.badge.KBadgeEmphasis
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.badge.KStatusChip
import tech.kloos.kompound.demo.DemoScope

private const val Usage_statusChip_basic = """import tech.kloos.kompound.badge.KStatusChip

// The label morphs smoothly as the phase changes.
KStatusChip(phase.label, busy = phase != Phase.Done)"""

@KompoundDemo(
    id = "status-chip.basic",
    title = "KStatusChip",
    description = "Live status label with a spinner or dot; the width glides when the phase text changes.",
    category = KompoundCategory.Display,
    tags = ["status", "chip", "badge", "progress", "streaming", "phase"],
    since = "0.1.0",
    usage = Usage_statusChip_basic,
)
@Composable
fun DemoScope.KStatusChipDemo() {
    val phase = choiceControl("Phase", listOf("Loading", "Prefilling", "Generating a long answer", "Done"))
    val busy = boolControl("Busy", true)
    val tone = choiceControl("Tone", KBadgeTone.entries)
    val emphasis = choiceControl("Emphasis", KBadgeEmphasis.entries)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KStatusChip(phase, tone = tone, busy = busy, emphasis = emphasis)
    }
}
