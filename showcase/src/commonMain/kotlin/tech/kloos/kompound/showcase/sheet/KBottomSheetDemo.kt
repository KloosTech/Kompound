package tech.kloos.kompound.showcase.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.sheet.KBottomSheet
import tech.kloos.kompound.sheet.KBottomSheetDefaults
import tech.kloos.kompound.text.KText

private enum class Mode(val label: String, val dialogFromWidth: Dp) {
    Adaptive("Adaptive (600dp)", KBottomSheetDefaults.DialogFromWidth),
    Sheet("Always sheet", 100_000.dp),
    Dialog("Always dialog", 1.dp),
}

private const val Usage_sheet_bottom = """import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.sheet.KBottomSheet
import tech.kloos.kompound.text.KText

var open by remember { mutableStateOf(false) }

KButton(onClick = { open = true }) { KText("Share") }

// A sheet on phones, a centred dialog on wide windows.
if (open) {
    KBottomSheet(onDismissRequest = { open = false }, title = "Share", showCloseButton = true) {
        KText("Anyone with the link can view.")
    }
}"""

@KompoundDemo(
    id = "sheet.bottom",
    title = "KBottomSheet",
    description = "Draggable bottom sheet on narrow windows that becomes a centred dialog on wide ones.",
    category = KompoundCategory.Overlays,
    tags = ["bottom sheet", "sheet", "modal", "adaptive", "overlay", "drawer"],
    since = "0.1.0",
    usage = Usage_sheet_bottom,
)
@Composable
fun DemoScope.KBottomSheetDemo() {
    val title = textControl("Title", "Share with")
    val close = boolControl("Close button", true)
    val mode = choiceControl("Form", Mode.entries, label = { it.label })
    val actions = boolControl("Action footer", true)
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        KButton(onClick = { open = true }) { KText("Open") }
    }
    if (open) {
        KBottomSheet(
            onDismissRequest = { open = false },
            title = title.ifEmpty { null },
            showCloseButton = close,
            dialogFromWidth = mode.dialogFromWidth,
            actions = if (actions) ({
                KButton(onClick = { open = false }, variant = KButtonVariant.Text) { KText("Done") }
            }) else null,
        ) {
            listOf("Ada Lovelace", "Grace Hopper", "Alan Turing", "Linus Torvalds").forEach { KListItem(it, onClick = { open = false }) }
        }
    }
}
