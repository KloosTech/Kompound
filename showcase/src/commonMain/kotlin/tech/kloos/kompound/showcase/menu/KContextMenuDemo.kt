package tech.kloos.kompound.showcase.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.menu.KContextMenuArea
import tech.kloos.kompound.menu.KMenuActionDivider
import tech.kloos.kompound.menu.KMenuActionGroup
import tech.kloos.kompound.menu.KMenuActionItem
import tech.kloos.kompound.text.KText

private const val Usage_context_menu = """import tech.kloos.kompound.menu.KContextMenuArea
import tech.kloos.kompound.menu.KMenuActionDivider
import tech.kloos.kompound.menu.KMenuActionItem

KContextMenuArea(
    actions = {                      // asked when the menu opens: look at the current selection here
        listOf(
            KMenuActionItem("Copy", onClick = { copy(selection) }),
            KMenuActionItem("Rename", onClick = { rename(selection) }, enabled = selection.size == 1),
            KMenuActionDivider,
            KMenuActionItem("Delete", onClick = { delete(selection) }),
        )
    },
) {
    FileList()                       // right-click, long press, or the Menu key while it has focus
}"""

@KompoundDemo(
    id = "menu.context",
    title = "KContextMenuArea",
    description = "A menu that opens at the pointer on right-click, long press or the Menu key, built from the same actions as KActionMenu.",
    category = KompoundCategory.Overlays,
    tags = ["context menu", "right click", "menu", "actions", "long press", "desktop"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_context_menu,
)
@Composable
fun DemoScope.KContextMenuDemo() {
    val enabled = boolControl("Enabled", true)
    val group = boolControl("Group", true)
    var last by remember { mutableStateOf("Right-click (or long-press) the box") }
    var pinned by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KContextMenuArea(
            actions = {
                listOf(
                    KMenuActionItem("Copy", { last = "Copy" }),
                    KMenuActionItem("Rename", { last = "Rename" }, supportingText = "F2"),
                    KMenuActionItem("Pinned", { pinned = !pinned; last = "Pinned: $pinned" }, selected = pinned, closeOnClick = false),
                    if (group) KMenuActionGroup("Move to", listOf(KMenuActionItem("Archive", { last = "Archive" }), KMenuActionItem("Trash", { last = "Trash" }))) else null,
                    KMenuActionDivider,
                    KMenuActionItem("Delete", { last = "Delete" }),
                )
            },
            enabled = enabled,
        ) {
            Box(
                Modifier.fillMaxWidth().height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) { KText("Right-click, long-press, or focus this box and press the Menu key") }
        }
        KText("Last action: $last")
    }
}
