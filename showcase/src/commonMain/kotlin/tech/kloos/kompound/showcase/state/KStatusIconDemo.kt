package tech.kloos.kompound.showcase.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.state.KStatus
import tech.kloos.kompound.state.KStatusIcon
import tech.kloos.kompound.text.KText

private const val Usage_statusIcon_basic = """import tech.kloos.kompound.state.KStatus
import tech.kloos.kompound.state.KStatusIcon

KStatusIcon(KStatus.Running)                 // spinner
KStatusIcon(KStatus.Error, size = 20.dp)     // cross, announced as "Failed" (localised)"""

@KompoundDemo(
    id = "status-icon.basic",
    title = "KStatusIcon",
    description = "Small mark for a task outcome: waiting ring, spinner, check, cross or dash, coloured by meaning and announced to screen readers.",
    category = KompoundCategory.Feedback,
    tags = ["status", "icon", "state", "tool", "result", "success", "error"],
    since = "0.1.0",
    usage = Usage_statusIcon_basic,
)
@Composable
fun DemoScope.KStatusIconDemo() {
    val status = choiceControl("Status", KStatus.entries)
    val size = floatControl("Size", 12f..40f, 16f)
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        KStatusIcon(status, size = size.dp)
        KText(status.name)
    }
}
