package tech.kloos.kompound.showcase.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.progress.KCircularProgress
import tech.kloos.kompound.progress.KLinearProgress
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "progress.indicators",
    title = "KLinearProgress and KCircularProgress",
    description = "Determinate and indeterminate progress bars and spinners, exposed as progress bars to screen readers.",
    category = KompoundCategory.Feedback,
    tags = ["progress", "loading", "spinner", "indicator", "bar"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KProgressDemo() {
    val indeterminate = boolControl("Indeterminate", false)
    val value = floatControl("Progress", 0f..1f, 0.6f)
    val progress = if (indeterminate) null else value
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KText("Linear")
        KLinearProgress(progress)
        KText("Circular")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            KCircularProgress(progress)
            KCircularProgress(progress, size = 24.dp, strokeWidth = 3.dp)
            KCircularProgress(progress, size = 64.dp, strokeWidth = 6.dp)
        }
    }
}
