package tech.kloos.kompound.command

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.dialog.KDialogDefaults
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.search.KFuzzy
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

/**
 * One entry of a [KCommandPalette].
 *
 * @property id Stable identity.
 * @property title What the command is called; what the query is matched against first.
 * @property onRun Does the work. The palette closes first.
 * @property subtitle A second line (where it lives, what it does); also matched.
 * @property icon A leading icon.
 * @property shortcut The keyboard shortcut to show on the right (`"Ctrl+S"`); display only.
 * @property keywords Extra words that find the command (`"preferences"` for Settings).
 * @property section Group name shown above the commands while the query is empty.
 * @property enabled A disabled command is listed dimmed and cannot run.
 */
@Immutable
public class KCommand(
    public val id: String,
    public val title: String,
    public val onRun: () -> Unit,
    public val subtitle: String? = null,
    public val icon: ImageVector? = null,
    public val shortcut: String? = null,
    public val keywords: List<String> = emptyList(),
    public val section: String? = null,
    public val enabled: Boolean = true,
)

private sealed interface Entry {
    class Header(val text: String) : Entry
    class Item(val command: KCommand, val titleMatches: List<Int>) : Entry
}

/** The commands of [commands] that match [query], best first; all of them in their given order for a blank query. */
internal fun rankCommands(commands: List<KCommand>, query: String): List<Pair<KCommand, List<Int>>> {
    if (query.isBlank()) return commands.map { it to emptyList() }
    return commands.mapNotNull { c ->
        val title = KFuzzy.match(query, c.title)
        val others = (listOfNotNull(c.subtitle) + c.keywords).mapNotNull { KFuzzy.match(query, it) }.maxOfOrNull { it.score }
        when {
            title != null -> Triple(c, title.score + 20, title.indices)
            others != null -> Triple(c, others, emptyList())
            else -> null
        }
    }.sortedByDescending { it.second }.map { it.first to it.third }
}

/**
 * A search box over every action of the app (the "Ctrl+K" menu): type to narrow the commands by fuzzy match on their title, subtitle and
 * keywords (`gts` finds "Go to Settings"), Up and Down move, Enter runs the highlighted command, Escape or a click outside closes. While the query is
 * empty the commands are listed in your order under their [KCommand.section] headings.
 *
 * Open it from a shortcut with [kCommandShortcut]. The palette closes (through [onDismissRequest]) before the command runs.
 *
 * @param open Whether the palette is shown.
 * @param onDismissRequest Called when the user closes it or after a command was chosen; set your `open` state to `false` there.
 * @param commands All commands.
 * @param placeholder Hint in the search box.
 * @param noResultsText Shown when nothing matches.
 * @param style Overrides merged over the panel style.
 */
@Composable
public fun KCommandPalette(
    open: Boolean,
    onDismissRequest: () -> Unit,
    commands: List<KCommand>,
    modifier: Modifier = Modifier,
    placeholder: String = KompoundTheme.strings.searchCommands,
    noResultsText: String = KompoundTheme.strings.noCommands,
    style: Style = Style,
) {
    if (!open) return
    remember { KompoundStyles.ensureEnabled() }
    var query by remember { mutableStateOf("") }
    var active by remember { mutableIntStateOf(0) }
    val ranked = remember(commands, query) { rankCommands(commands, query) }
    val entries = remember(ranked, query) {
        buildList {
            var lastSection: String? = null
            for ((c, indices) in ranked) {
                if (query.isBlank() && c.section != null && c.section != lastSection) { add(Entry.Header(c.section)); lastSection = c.section }
                add(Entry.Item(c, indices))
            }
        }
    }
    val items = entries.filterIsInstance<Entry.Item>()
    val activeIndex = active.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val panel = remember { MutableStyleState(null) }
    val scheme = MaterialTheme.colorScheme
    val dialogStyle = KDialogDefaults.style()
    val compact = remember { Style { contentPadding(12.dp) } }

    fun run(c: KCommand) {
        if (!c.enabled) return
        onDismissRequest()
        c.onRun()
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(activeIndex, entries) {
        val position = entries.indexOfFirst { it is Entry.Item && it === items.getOrNull(activeIndex) }
        if (position < 0) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || position <= visible.first().index || position >= visible.last().index) list.scrollToItem((position - 1).coerceAtLeast(0))
    }
    Dialog(onDismissRequest, DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(KDialogDefaults.scrim(scheme.scrim))
                .pointerInput(Unit) { detectTapGestures { onDismissRequest() } },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { active = (activeIndex + 1).coerceAtMost((items.size - 1).coerceAtLeast(0)); true }
                            Key.DirectionUp -> { active = (activeIndex - 1).coerceAtLeast(0); true }
                            Key.Enter, Key.NumPadEnter -> { items.getOrNull(activeIndex)?.let { run(it.command) }; true }
                            else -> false
                        }
                    }
                    .styleable(panel, dialogStyle, compact, style),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KTextField(
                    value = query, onValueChange = { query = it; active = 0 },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = placeholder,
                    leading = { KIcon(KompoundIcons.Search, contentDescription = null) },
                )
                if (entries.isEmpty()) {
                    KText(noResultsText, Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth(), list) {
                        items(entries) { entry ->
                            when (entry) {
                                is Entry.Header -> KText(
                                    entry.text,
                                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
                                    style = Style { textStyle(androidx.compose.ui.text.TextStyle(color = scheme.onSurfaceVariant)) },
                                )
                                is Entry.Item -> CommandRow(entry, selected = entry === items.getOrNull(activeIndex), onRun = { run(entry.command) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(entry: Entry.Item, selected: Boolean, onRun: () -> Unit) {
    val c = entry.command
    val scheme = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (c.enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f))
    val shortcutStyle = remember(scheme) { Style { textStyle(androidx.compose.ui.text.TextStyle(color = scheme.onSurfaceVariant)) } }
    KListItem(
        headline = {
            if (entry.titleMatches.isEmpty()) KText(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else KText(KFuzzy.highlight(c.title, entry.titleMatches, scheme.primary), textStyle = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supporting = c.subtitle?.let { { KText(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leading = c.icon?.let { icon -> { KIcon(icon, contentDescription = null) } },
        trailing = c.shortcut?.let { s -> { KText(s, style = shortcutStyle) } },
        onClick = onRun, selected = selected, enabled = c.enabled,
    )
}

/**
 * Calls [onTrigger] when Ctrl+K (Cmd+K on macOS) is pressed while this node or something inside it has focus: put it on the root of your
 * screen and open the [KCommandPalette] from there. Pass another [key] for a different shortcut.
 */
public fun Modifier.kCommandShortcut(key: Key = Key.K, onTrigger: () -> Unit): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && e.key == key && (e.isCtrlPressed || e.isMetaPressed)) { onTrigger(); true } else false
}
