package tech.kloos.kompound.showcase.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.selection.KSwitch
import tech.kloos.kompound.settings.KSettingsFilter
import tech.kloos.kompound.settings.KSettingsGroup
import tech.kloos.kompound.settings.KSettingsRow
import tech.kloos.kompound.settings.KSettingsTextField

private const val Usage_settings_basic = """import tech.kloos.kompound.settings.*

KSettingsFilter(query) {
    KSettingsGroup("Model") {
        KSettingsRow("Server URL", supporting = "Where the model runs") {
            KSettingsTextField(url, onCommit = { url = it })   // saves on Enter or blur, shows "Saved"
        }
        KSettingsRow("Stream answers", keywords = listOf("sse")) { KSwitch(stream, { stream = it }, Modifier.semantics { contentDescription = "Stream answers" }) }
    }
}"""

@KompoundDemo(
    id = "settings.basic",
    title = "KSettingsGroup",
    description = "Settings screen parts: groups, label-plus-control rows, an auto-save text field and a text filter that hides rows and empty groups.",
    category = KompoundCategory.Layout,
    tags = ["settings", "preferences", "row", "group", "filter", "autosave"],
    since = "0.1.0",
    usage = Usage_settings_basic,
)
@Composable
fun DemoScope.KSettingsDemo() {
    val query = textControl("Filter", "")
    var url by remember { mutableStateOf("http://localhost:11434") }
    var stream by remember { mutableStateOf(true) }
    var notify by remember { mutableStateOf(false) }
    KSettingsFilter(query, Modifier.padding(16.dp)) {
        KSettingsGroup("Model") {
            KSettingsRow("Server URL", supporting = "Where the model runs") { KSettingsTextField(url, onCommit = { url = it }) }
            KSettingsRow("Stream answers", keywords = listOf("sse")) { KSwitch(stream, { stream = it }, Modifier.semantics { contentDescription = "Stream answers" }) }
        }
        KSettingsGroup("General") {
            KSettingsRow("Notifications", supporting = "Tell me when a long task is done") { KSwitch(notify, { notify = it }, Modifier.semantics { contentDescription = "Notifications" }) }
        }
    }
}
