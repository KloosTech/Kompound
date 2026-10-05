package tech.kloos.kompound.showcase.command

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
import tech.kloos.kompound.command.KCommand
import tech.kloos.kompound.command.KCommandPalette
import tech.kloos.kompound.command.kCommandShortcut
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

private const val Usage_command = """import tech.kloos.kompound.command.KCommand
import tech.kloos.kompound.command.KCommandPalette
import tech.kloos.kompound.command.kCommandShortcut

var open by remember { mutableStateOf(false) }

Box(Modifier.kCommandShortcut { open = true }) {      // Ctrl+K / Cmd+K
    AppContent()
}
KCommandPalette(
    open = open,
    onDismissRequest = { open = false },
    commands = listOf(
        KCommand("save", "Save file", onRun = { save() }, shortcut = "Ctrl+S", section = "File"),
        KCommand("settings", "Go to Settings", onRun = { navigate(Settings) }, keywords = listOf("preferences")),
    ),
)"""

@KompoundDemo(
    id = "command.palette",
    title = "KCommandPalette",
    description = "A Ctrl+K search over every action of the app: fuzzy matching on title, subtitle and keywords, sections, shortcuts, keyboard first.",
    category = KompoundCategory.Overlays,
    tags = ["command palette", "search", "actions", "ctrl k", "spotlight", "fuzzy"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_command,
)
@Composable
fun DemoScope.KCommandPaletteDemo() {
    val sections = boolControl("Sections", true)
    val icons = boolControl("Icons", true)
    var open by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf("Nothing run yet") }
    fun cmd(id: String, title: String, section: String, shortcut: String? = null, keywords: List<String> = emptyList(), subtitle: String? = null, enabled: Boolean = true) =
        KCommand(
            id, title, { last = "Ran: $title" }, subtitle = subtitle, icon = if (icons) DemoIcons.Star else null, shortcut = shortcut, keywords = keywords,
            section = if (sections) section else null, enabled = enabled,
        )
    val commands = listOf(
        cmd("new", "New file", "File", "Ctrl+N"),
        cmd("open", "Open file", "File", "Ctrl+O"),
        cmd("save", "Save file", "File", "Ctrl+S", subtitle = "Writes the current document"),
        cmd("export", "Export as PDF", "File", keywords = listOf("print")),
        cmd("settings", "Go to Settings", "Navigate", "Ctrl+,", keywords = listOf("preferences", "options")),
        cmd("home", "Go to Home", "Navigate"),
        cmd("dark", "Toggle dark theme", "View", keywords = listOf("appearance")),
        cmd("zoom", "Zoom in", "View", "Ctrl++"),
        cmd("share", "Share (needs sign-in)", "Account", enabled = false),
    )
    Column(Modifier.padding(16.dp).kCommandShortcut { open = true }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KButton(onClick = { open = true }) { KText("Open command palette") }
        KText("Or press Ctrl+K (Cmd+K) while this page has focus. Try typing \"gts\" or \"pref\".")
        KText(last)
    }
    KCommandPalette(open, { open = false }, commands)
}
