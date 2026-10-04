package tech.kloos.kompound.showcase.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.split.KSplitPane
import tech.kloos.kompound.split.rememberKSplitPaneState
import tech.kloos.kompound.text.KText

private const val Usage_split_pane = """import tech.kloos.kompound.split.KSplitPane
import tech.kloos.kompound.split.rememberKSplitPaneState

val split = rememberKSplitPaneState(initialFraction = 0.3f)   // saved across configuration changes

KSplitPane(
    first = { FileTree() },
    second = { Editor() },
    modifier = Modifier.fillMaxSize(),
    state = split,                       // split.fraction is the first pane's share, 0..1
    minFirst = 160.dp,
    minSecond = 240.dp,
)
// Nest them for three panes. Vertical panes: orientation = Orientation.Vertical."""

@KompoundDemo(
    id = "split.pane",
    title = "KSplitPane",
    description = "Two panes with a draggable divider, side by side or stacked, with minimum sizes and keyboard control.",
    category = KompoundCategory.Layout,
    tags = ["split", "pane", "divider", "resize", "layout", "panel"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_split_pane,
)
@Composable
fun DemoScope.KSplitPaneDemo() {
    val vertical = boolControl("Vertical", false)
    val nested = boolControl("Three panes (nested)", false)
    val state = rememberKSplitPaneState(0.35f)
    val inner = rememberKSplitPaneState(0.5f)
    val frame = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    val orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal
    @Composable fun Pane(text: String, tint: Float) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = tint)).padding(12.dp)) { KText(text) }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KSplitPane(
            first = { Pane("First pane (${(state.fraction * 100).toInt()}%)", 0.08f) },
            second = {
                if (nested) KSplitPane(
                    first = { Pane("Second", 0.16f) }, second = { Pane("Third", 0.24f) },
                    state = inner, orientation = if (vertical) Orientation.Horizontal else Orientation.Vertical,
                ) else Pane("Second pane", 0.16f)
            },
            modifier = frame, state = state, orientation = orientation, minFirst = 100.dp, minSecond = 100.dp,
        )
        KText("Drag the divider, or focus it and use the arrow keys, Home and End.")
    }
}
