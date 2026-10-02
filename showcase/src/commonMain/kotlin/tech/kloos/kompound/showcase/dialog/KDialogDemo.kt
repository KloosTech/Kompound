package tech.kloos.kompound.showcase.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.dialog.KAlertDialog
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.text.KText

private const val Usage_dialog_basic = """import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KAlertDialog
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.text.KText

var showDelete by remember { mutableStateOf(false) }

// Confirmation in one call.
if (showDelete) {
    KAlertDialog(
        onDismissRequest = { showDelete = false },
        title = "Delete file?",
        message = "This cannot be undone.",
        confirmText = "Delete",
        onConfirm = { delete(); showDelete = false },
        dismissText = "Cancel",
    )
}

// Free content with your own actions.
var showInfo by remember { mutableStateOf(false) }
if (showInfo) {
    KDialog(
        onDismissRequest = { showInfo = false },
        title = "About",
        actions = { KButton(onClick = { showInfo = false }, variant = KButtonVariant.Text) { KText("Close") } },
    ) {
        KText("Kompound 0.1.0")
    }
}"""

@KompoundDemo(
    id = "dialog.basic",
    title = "KDialog and KAlertDialog",
    description = "Modal dialog with title, scrolling content, action footer, close button and full-screen mode.",
    category = KompoundCategory.Overlays,
    tags = ["dialog", "modal", "alert", "confirm", "popup", "overlay"],
    since = "0.1.0",
    usage = Usage_dialog_basic,
)
@Composable
fun DemoScope.KDialogDemo() {
    val title = textControl("Title", "Rename project")
    val fullScreen = boolControl("Full screen", false)
    val close = boolControl("Close button", false)
    val outside = boolControl("Dismiss on outside click", true)
    val longContent = boolControl("Long content (scrolls)", false)
    var dialog by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KButton(onClick = { dialog = true }) { KText("Open dialog") }
        KButton(onClick = { alert = true }, variant = KButtonVariant.Tonal) { KText("Open alert dialog") }
        if (result.isNotEmpty()) KText(result)
    }
    if (dialog) {
        KDialog(
            onDismissRequest = { dialog = false },
            title = title.ifEmpty { null },
            fullScreen = fullScreen,
            showCloseButton = close,
            dismissOnClickOutside = outside,
            actions = {
                KButton(onClick = { dialog = false }, variant = KButtonVariant.Text) { KText("Cancel") }
                KButton(onClick = { result = "Saved"; dialog = false }, variant = KButtonVariant.Text) { KText("Save") }
            },
        ) {
            KText("Dialogs ask for a decision or a small amount of input and block the screen behind them.")
            if (longContent) repeat(30) { KText("Line ${it + 1} of scrolling content.", Modifier.padding(top = 8.dp)) }
        }
    }
    if (alert) {
        KAlertDialog(
            onDismissRequest = { alert = false },
            title = "Delete project?",
            message = "This removes the project and all its files. It cannot be undone.",
            confirmText = "Delete", dismissText = "Keep",
            onConfirm = { result = "Deleted"; alert = false },
        )
    }
}
