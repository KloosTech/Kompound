package tech.kloos.kompound.showcase.textfield

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
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.textfield.KNumberField
import tech.kloos.kompound.textfield.KTextArea
import tech.kloos.kompound.textfield.KTextField

private const val Usage_textfield_basic = """import tech.kloos.kompound.textfield.KTextField

var email by remember { mutableStateOf("") }
val invalid = email.isNotEmpty() && '@' !in email

KTextField(
    value = email,
    onValueChange = { email = it },
    label = "Email",
    placeholder = "name@example.com",
    supportingText = if (invalid) "Enter a valid address" else null,
    isError = invalid,
    modifier = Modifier.fillMaxWidth(),
)"""

@KompoundDemo(
    id = "textfield.basic",
    title = "KTextField",
    description = "Outlined text field with label, placeholder, supporting text, error state and icon slots.",
    category = KompoundCategory.Inputs,
    tags = ["text", "field", "input", "form", "outlined"],
    since = "0.1.0",
    usage = Usage_textfield_basic,
)
@Composable
fun DemoScope.KTextFieldDemo() {
    val label = textControl("Label", "Email")
    val placeholder = textControl("Placeholder", "name@example.com")
    val supporting = textControl("Supporting text", "We never share it")
    val isError = boolControl("Error", false)
    val enabled = boolControl("Enabled", true)
    val readOnly = boolControl("Read only", false)
    val limit = boolControl("Max length 20", false)
    val leading = boolControl("Leading icon", false)
    val trailing = boolControl("Trailing icon", false)
    var value by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        KTextField(
            value = value, onValueChange = { value = it },
            label = label.ifEmpty { null }, placeholder = placeholder.ifEmpty { null },
            supportingText = supporting.ifEmpty { null },
            isError = isError, enabled = enabled, readOnly = readOnly,
            maxLength = if (limit) 20 else null,
            leading = if (leading) ({ KIcon(DemoIcons.Star, null) }) else null,
            trailing = if (trailing) ({ KIcon(DemoIcons.Check, null) }) else null,
        )
    }
}

private const val Usage_textfield_area = """import tech.kloos.kompound.textfield.KTextArea

var notes by remember { mutableStateOf("") }

KTextArea(
    value = notes,
    onValueChange = { notes = it },
    label = "Notes",
    minLines = 3,
    maxLines = 8,
    maxLength = 280,
    modifier = Modifier.fillMaxWidth(),
)"""

@KompoundDemo(
    id = "textfield.area",
    title = "KTextArea",
    description = "Multi-line text field that grows from three to eight lines before scrolling.",
    category = KompoundCategory.Inputs,
    tags = ["text", "area", "multiline", "input", "form"],
    since = "0.1.0",
    usage = Usage_textfield_area,
)
@Composable
fun DemoScope.KTextAreaDemo() {
    val enabled = boolControl("Enabled", true)
    var value by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        KTextArea(value, { value = it }, label = "Notes", placeholder = "Write something", enabled = enabled, maxLength = 200)
    }
}

private const val Usage_textfield_number = """import tech.kloos.kompound.textfield.KNumberField

// The value is a String so partial input such as "-" or "1." stays editable; parse it where you need it.
var amount by remember { mutableStateOf("") }

KNumberField(
    value = amount,
    onValueChange = { amount = it },
    label = "Amount",
    allowDecimal = true,
    allowNegative = false,
    modifier = Modifier.fillMaxWidth(),
)
val parsed: Double? = amount.toDoubleOrNull()"""

@KompoundDemo(
    id = "textfield.number",
    title = "KNumberField",
    description = "Text field that only accepts digits, with optional decimal point and minus sign.",
    category = KompoundCategory.Inputs,
    tags = ["number", "numeric", "input", "form", "decimal"],
    since = "0.1.0",
    usage = Usage_textfield_number,
)
@Composable
fun DemoScope.KNumberFieldDemo() {
    val decimal = boolControl("Allow decimal", false)
    val negative = boolControl("Allow negative", false)
    val enabled = boolControl("Enabled", true)
    var value by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        KNumberField(value, { value = it }, allowDecimal = decimal, allowNegative = negative, label = "Quantity", enabled = enabled,
            supportingText = value.toDoubleOrNull()?.let { "Parsed: $it" })
    }
}
