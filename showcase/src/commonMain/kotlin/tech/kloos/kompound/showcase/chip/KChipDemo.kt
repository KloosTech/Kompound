package tech.kloos.kompound.showcase.chip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "chip.basic",
    title = "KChip",
    description = "Compact chip for actions, filters and input values, with leading and trailing slots.",
    category = KompoundCategory.Inputs,
    tags = ["chip", "filter", "tag", "input", "assist"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KChipDemo() {
    val label = textControl("Label", "Chip")
    val enabled = boolControl("Enabled", true)
    val leading = boolControl("Leading icon", false)
    val trailing = boolControl("Trailing icon", false)
    var filterA by remember { mutableStateOf(true) }
    var filterB by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KText("Action chip")
        KChip(
            label, onClick = {}, enabled = enabled,
            leading = if (leading) ({ KIcon(DemoIcons.Star, null) }) else null,
            trailing = if (trailing) ({ KIcon(DemoIcons.Check, null) }) else null,
        )
        KText("Filter chips")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KChip("Selected", onClick = { filterA = !filterA }, selected = filterA, enabled = enabled,
                leading = if (filterA) ({ KIcon(DemoIcons.Check, null) }) else null)
            KChip("Not selected", onClick = { filterB = !filterB }, selected = filterB, enabled = enabled)
        }
    }
}
