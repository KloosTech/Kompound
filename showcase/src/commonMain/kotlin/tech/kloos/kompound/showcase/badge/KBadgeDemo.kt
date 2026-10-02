package tech.kloos.kompound.showcase.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeDot
import tech.kloos.kompound.badge.KBadgeEmphasis
import tech.kloos.kompound.badge.KBadgeShape
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_badge_basic = """import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeDot
import tech.kloos.kompound.badge.KBadgeEmphasis
import tech.kloos.kompound.badge.KBadgeTone

KBadge("3")                                                   // strong error badge, like a counter
KBadge("Stable", tone = KBadgeTone.Success, emphasis = KBadgeEmphasis.Subtle)
KBadgeDot(tone = KBadgeTone.Info, contentDescription = "New")"""

@KompoundDemo(
    id = "badge.basic",
    title = "KBadge",
    description = "Small status or count label in six tones, strong or subtle, pill or square, plus a bare dot.",
    category = KompoundCategory.Display,
    tags = ["badge", "status", "count", "label", "tag"],
    since = "0.1.0",
    usage = Usage_badge_basic,
)
@Composable
fun DemoScope.KBadgeDemo() {
    val text = textControl("Text", "12")
    val tone = choiceControl("Tone", KBadgeTone.entries)
    val emphasis = choiceControl("Emphasis", KBadgeEmphasis.entries)
    val shape = choiceControl("Shape", KBadgeShape.entries)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KBadge(text, tone = tone, emphasis = emphasis, shape = shape)
            KBadgeDot(tone = tone, emphasis = emphasis)
        }
        KText("All tones")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KBadgeTone.entries.forEach { KBadge(it.name, tone = it, emphasis = emphasis, shape = shape) }
        }
    }
}
