package tech.kloos.kompound.showcase.border

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.border.dashedBorder
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText

private const val Usage_border_dashed = """import tech.kloos.kompound.border.dashedBorder
import tech.kloos.kompound.text.KText

// A drop zone: the dashes follow the shape and are drawn inside the bounds.
val line = MaterialTheme.colorScheme.outline
Box(
    Modifier
        .fillMaxWidth()
        .height(120.dp)
        .dashedBorder(color = line, shape = RoundedCornerShape(16.dp), width = 2.dp, dashLength = 8.dp, gapLength = 6.dp),
    contentAlignment = Alignment.Center,
) {
    KText("Drop files here")
}"""

@KompoundDemo(
    id = "border.dashed",
    title = "Dashed border",
    description = "Modifier that draws a dashed border in any shape, for drop zones and placeholders.",
    category = KompoundCategory.Utilities,
    tags = ["border", "dashed", "modifier", "drop zone", "placeholder", "outline"],
    since = "0.1.0",
    usage = Usage_border_dashed,
)
@Composable
fun DemoScope.DashedBorderDemo() {
    val width = floatControl("Width (dp)", 1f..6f, 2f)
    val dash = floatControl("Dash (dp)", 2f..24f, 8f)
    val gap = floatControl("Gap (dp)", 0f..24f, 6f)
    val radius = floatControl("Corner radius (dp)", 0f..48f, 16f)
    val round = boolControl("Round caps", true)
    val line = MaterialTheme.colorScheme.outline
    Column(Modifier.padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth().height(120.dp).dashedBorder(line, RoundedCornerShape(radius.dp), width.dp, dash.dp, gap.dp, if (round) StrokeCap.Round else StrokeCap.Butt),
            contentAlignment = Alignment.Center,
        ) { KText("Drop files here") }
    }
}
