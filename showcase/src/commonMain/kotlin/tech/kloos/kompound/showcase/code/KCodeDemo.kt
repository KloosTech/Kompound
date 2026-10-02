package tech.kloos.kompound.showcase.code

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import tech.kloos.kompound.code.KCode
import tech.kloos.kompound.code.KCodeColors
import tech.kloos.kompound.code.KCodeDefaults
import tech.kloos.kompound.code.KCodeLanguage
import tech.kloos.kompound.demo.DemoScope

private const val KotlinSample = """// A chip that never changes height
@Composable
public fun KChip(
    label: String,
    onClick: () -> Unit,
    selected: Boolean? = null,
) {
    val padding = 12.dp
    if (selected == true) {
        KText("Selected: ${'$'}label", maxLines = 1)
    }
    /* trailing slot */
    return Unit
}"""

private const val JsonSample = """{
  "name": "Kompound",
  "version": "0.1.0",
  "targets": ["android", "ios", "desktop", "wasm"],
  "stable": false,
  "downloads": 1280,
  "license": null
}"""

private const val Usage_code_view = """import tech.kloos.kompound.code.KCode
import tech.kloos.kompound.code.KCodeColors
import tech.kloos.kompound.code.KCodeLanguage

// Read-only, selectable and highlighted.
KCode(code = "val greeting = \"Hello\"", language = KCodeLanguage.Kotlin)

// Editable: pass onCodeChange. Json, Plain or your own KCodeLanguage also work.
var json by remember { mutableStateOf("{ \"name\": \"Kompound\" }") }
KCode(
    code = json,
    onCodeChange = { json = it },
    language = KCodeLanguage.Json,
    showLineNumbers = false,
    colors = KCodeColors.OneDark,
)"""

@KompoundDemo(
    id = "code.view",
    title = "KCode",
    description = "Syntax-highlighted code in a selectable text field, read-only or editable, with line numbers and Kotlin and JSON built in.",
    category = KompoundCategory.Display,
    tags = ["code", "syntax", "highlight", "editor", "monospace", "text field", "json", "kotlin"],
    since = "0.1.0",
    status = "Beta",
    usage = Usage_code_view,
)
@Composable
fun DemoScope.KCodeDemo() {
    val language = choiceControl("Language", listOf("Kotlin", "JSON", "Plain"))
    val palette = choiceControl("Palette", listOf("Theme", "One Dark"))
    val lineNumbers = boolControl("Line numbers", true)
    val editable = boolControl("Editable", false)
    var kotlin by remember { mutableStateOf(KotlinSample) }
    var json by remember { mutableStateOf(JsonSample) }
    val (code, setCode) = when (language) {
        "JSON" -> json to { v: String -> json = v }
        else -> kotlin to { v: String -> kotlin = v }
    }
    Column(Modifier.padding(16.dp)) {
        KCode(
            code = code,
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            onCodeChange = if (editable) setCode else null,
            language = when (language) {
                "JSON" -> KCodeLanguage.Json
                "Plain" -> KCodeLanguage.Plain
                else -> KCodeLanguage.Kotlin
            },
            showLineNumbers = lineNumbers,
            colors = if (palette == "One Dark") KCodeColors.OneDark else KCodeDefaults.colors(),
        )
    }
}
