package tech.kloos.kompound.showcase.inline

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
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.inline.KInlineEdit
import tech.kloos.kompound.text.KText

private const val Usage_inline_edit = """import tech.kloos.kompound.inline.KInlineEdit

var title by remember { mutableStateOf("Quarterly report") }

// Shows the text; the pencil switches to a field with save and cancel. validate returns an error or null.
KInlineEdit(
    value = title,
    onValueChange = { title = it },     // called when the user saves
    validate = { if (it.isBlank()) "A title is required" else null },
)"""

@KompoundDemo(
    id = "inline.edit",
    title = "KInlineEdit",
    description = "Text that becomes a field on click; Enter or check saves, Escape or close cancels, with validation.",
    category = KompoundCategory.Inputs,
    tags = ["inline", "edit", "rename", "text", "form"],
    since = "0.1.0",
    usage = Usage_inline_edit,
)
@Composable
fun DemoScope.KInlineEditDemo() {
    val enabled = boolControl("Enabled", true)
    val required = boolControl("Required (validation)", true)
    var title by remember { mutableStateOf("Quarterly report") }
    var note by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KText("Title")
        KInlineEdit(
            value = title, onValueChange = { title = it }, enabled = enabled, placeholder = "Add a title",
            validate = { if (required && it.isBlank()) "A title is required" else null },
        )
        KText("Note (multi-line)")
        KInlineEdit(value = note, onValueChange = { note = it }, enabled = enabled, placeholder = "Add a note", singleLine = false)
    }
}
