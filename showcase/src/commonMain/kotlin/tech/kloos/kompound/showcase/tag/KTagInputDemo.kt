package tech.kloos.kompound.showcase.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import tech.kloos.kompound.tag.KTagInput
import tech.kloos.kompound.text.KText

private const val Usage_tags = """import tech.kloos.kompound.tag.KTagInput

var tags by remember { mutableStateOf(listOf("kotlin")) }

KTagInput(
    tags = tags,
    onTagsChange = { tags = it },
    label = "Topics",
    suggestions = listOf("compose", "multiplatform", "design", "accessibility"),
    validate = { if (it.length > 20) "Too long" else null },
    maxTags = 8,
)
// Enter or "," ends a tag, Backspace removes the last one, a click on a tag removes it."""

@KompoundDemo(
    id = "tag.input",
    title = "KTagInput",
    description = "Turns typed text into removable tags: Enter or a comma ends a tag, with suggestions, validation and a limit.",
    category = KompoundCategory.Inputs,
    tags = ["tags", "chips", "token", "multi", "recipients", "labels"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_tags,
)
@Composable
fun DemoScope.KTagInputDemo() {
    val enabled = boolControl("Enabled", true)
    val duplicates = boolControl("Allow duplicates", false)
    val suggest = boolControl("Suggestions", true)
    val max = floatControl("Max tags", 1f..12f, 8f).toInt()
    var tags by remember { mutableStateOf(listOf("kotlin")) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KTagInput(
            tags, { tags = it }, Modifier.fillMaxWidth(), label = "Topics", placeholder = "Add a topic", enabled = enabled,
            suggestions = if (suggest) listOf("compose", "multiplatform", "design", "accessibility", "theming", "charts") else emptyList(),
            allowDuplicates = duplicates, maxTags = max,
            validate = { if (it.length > 20) "At most 20 characters" else null },
        )
        KText("${tags.size} tag(s): ${tags.joinToString()}")
    }
}
