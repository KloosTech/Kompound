package tech.kloos.kompound.showcase.slide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.slide.KSlideToConfirm
import tech.kloos.kompound.text.KText

private const val Usage_slide_confirm = """import tech.kloos.kompound.slide.KSlideToConfirm

// The user has to drag the handle across; a short drag springs back.
KSlideToConfirm(
    label = "Slide to delete account",
    confirmedLabel = "Deleting...",        // replaces the label once the handle reached the end
    onConfirm = { deleteAccount() },       // called after a short delay, so the confirmed state is seen
)

// Screen readers and keyboards confirm without dragging (click action, Enter or Space).
// Keep the handle at the end afterwards, or use your own icon:
KSlideToConfirm(
    label = "Slide to pay",
    onConfirm = { pay() },
    resetAfterMillis = null,
    handle = { KText("€") },
)"""

@KompoundDemo(
    id = "slide.confirm",
    title = "KSlideToConfirm",
    description = "Drag-to-confirm control for actions that must not happen by accident; keyboard and screen readers confirm directly.",
    category = KompoundCategory.Inputs,
    tags = ["slide", "swipe", "confirm", "slider", "gesture", "dangerous", "action"],
    since = "0.1.0",
    status = "Beta",
    usage = Usage_slide_confirm,
)
@Composable
fun DemoScope.KSlideToConfirmDemo() {
    val enabled = boolControl("Enabled", true)
    val animated = boolControl("Animated hint", true)
    val label = textControl("Label", "Slide to delete")
    val confirmed = textControl("Confirmed label", "Deleting...")
    var confirmations by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KSlideToConfirm(label, { confirmations++ }, confirmedLabel = confirmed.ifBlank { null }, enabled = enabled, animated = animated)
        KText("Confirmed $confirmations times")
    }
}
