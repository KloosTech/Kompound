package tech.kloos.kompound.showcase.markdown

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
import tech.kloos.kompound.markdown.KMarkdown
import tech.kloos.kompound.markdown.KMarkdownField

private const val Usage_markdown_view = """import tech.kloos.kompound.markdown.KMarkdown
import tech.kloos.kompound.markdown.KMarkdownField

// Render a document. Links open through the platform unless you handle them.
KMarkdown(
    markdown = "# Release notes\n\nThis is **bold**, *italic* and `code`.\n\n- [x] Ship it\n- [ ] Celebrate",
    onLinkClick = { url -> openInBrowser(url) },
    selectable = true,
)

// Write Markdown: the source stays as typed with live styling, and KMarkdown shows the result.
var notes by remember { mutableStateOf("# Hello\n\nType *here*.") }
Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    KMarkdownField(value = notes, onValueChange = { notes = it }, label = "Notes", modifier = Modifier.fillMaxWidth())
    KMarkdown(notes)
}"""

private const val Sample = """# Kompound Markdown

Write **bold**, *italic*, ~~strikethrough~~ and `inline code`, or link to the [docs](https://kompound.kloos.tech/).

## Lists

- Buttons
  - Filled and tonal
  - With a loading spinner
- Inputs
1. Install
2. Wrap your app in `KompoundTheme`

- [x] Written
- [ ] Reviewed

> Quotes can hold **formatting** and
> run over several lines.

```kotlin
KButton(onClick = { save() }) {
    KText("Save")
}
```

| Component | Status |
|:----------|-------:|
| KButton   | stable |
| KCode     | beta   |

---

Hard break  
on the next line."""

@KompoundDemo(
    id = "markdown.view",
    title = "KMarkdown",
    description = "Renders Markdown with headings, lists, tasks, quotes, tables and highlighted code; KMarkdownField edits the source with live styling.",
    category = KompoundCategory.Display,
    tags = ["markdown", "text", "rich text", "document", "editor", "preview", "code"],
    since = "0.1.0",
    status = "Beta",
    usage = Usage_markdown_view,
)
@Composable
fun DemoScope.KMarkdownDemo() {
    val editable = boolControl("Edit source", false)
    val selectable = boolControl("Selectable", true)
    var source by remember { mutableStateOf(Sample) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (editable) KMarkdownField(source, { source = it }, Modifier.fillMaxWidth(), label = "Markdown source", minLines = 6, maxLines = 12)
        KMarkdown(source, Modifier.fillMaxWidth(), onLinkClick = {}, selectable = selectable)
    }
}
