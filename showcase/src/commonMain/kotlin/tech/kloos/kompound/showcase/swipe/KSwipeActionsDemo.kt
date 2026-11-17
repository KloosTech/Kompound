package tech.kloos.kompound.showcase.swipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.surface.KSurface
import tech.kloos.kompound.swipe.KSwipeAction
import tech.kloos.kompound.swipe.KSwipeActions
import tech.kloos.kompound.swipe.KSwipeSide
import tech.kloos.kompound.text.KText

private const val Usage_swipe_basic = """import tech.kloos.kompound.swipe.*

KSwipeActions(
    onAction = { side -> if (side == KSwipeSide.Start) confirmDelete() else archive() },   // the row springs back, you decide
    startAction = KSwipeAction("Delete", error, onError) { KIcon(KIcons.Delete, null) },
    endAction = KSwipeAction("Archive", primary, onPrimary) { KIcon(KIcons.Check, null) },
) {
    KListItem("Session 1")
}"""

@KompoundDemo(
    id = "swipe.actions",
    title = "KSwipeActions",
    description = "Swipe a row sideways to reveal an action; past the threshold it fires and the row springs back so you can confirm first. RTL aware.",
    category = KompoundCategory.Layout,
    tags = ["swipe", "dismiss", "row", "action", "list", "delete", "archive"],
    since = "0.1.0",
    usage = Usage_swipe_basic,
)
@Composable
fun DemoScope.KSwipeActionsDemo() {
    val threshold = floatControl("Threshold", 0.2f..0.8f, 0.4f)
    val both = boolControl("Both sides", true)
    var last by remember { mutableStateOf("none") }
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KSwipeActions(
            onAction = { last = if (it == KSwipeSide.Start) "Delete" else "Archive" },
            startAction = KSwipeAction("Delete", c.error, c.onError) { KIcon(KIcons.Delete, null) },
            endAction = if (both) KSwipeAction("Archive", c.primary, c.onPrimary) { KIcon(KIcons.Check, null) } else null,
            threshold = threshold,
        ) {
            KSurface { KListItem("Session with the agent", supporting = "Swipe me") }
        }
        KText("Last action: $last")
    }
}
