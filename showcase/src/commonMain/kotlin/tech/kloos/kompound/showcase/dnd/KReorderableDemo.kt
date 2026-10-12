package tech.kloos.kompound.showcase.dnd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import tech.kloos.kompound.annotations.KompoundStatus
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.dnd.KReorderableColumn
import tech.kloos.kompound.dnd.KReorderableLazyColumn
import tech.kloos.kompound.dnd.KReorderableRow
import tech.kloos.kompound.text.KText

private const val Usage_reorderable = """import tech.kloos.kompound.dnd.KReorderableColumn

var tasks by remember { mutableStateOf(listOf("Write", "Review", "Ship")) }

KReorderableColumn(
    items = tasks,
    onReorder = { tasks = it },      // once per drop or keyboard move, with the new order
    key = { it },
) { task ->
    KText(task)                      // row scope: isDragging, Modifier.kDragHandle()
}
// Long lists: KReorderableLazyColumn. Horizontal: KReorderableRow.
// Keyboard: focus a row, Alt+Up / Alt+Down. Screen readers: the Move up / Move down actions."""

private enum class Layout(val label: String) { Column("Column"), Lazy("Lazy column (40 items)"), Row("Row") }

@KompoundDemo(
    id = "dnd.reorderable",
    title = "KReorderableColumn",
    description = "Lists the user reorders by dragging a handle, with the keyboard (Alt+arrows) or screen reader actions.",
    category = KompoundCategory.Layout,
    tags = ["drag", "drop", "reorder", "sortable", "list", "handle"],
    since = "0.2.0",
    status = KompoundStatus.Beta,
    usage = Usage_reorderable,
)
@Composable
fun DemoScope.KReorderableDemo() {
    val layout = choiceControl("Layout", Layout.entries, Layout.Column) { it.label }
    val handle = boolControl("Drag handle", true)
    val enabled = boolControl("Enabled", true)
    val longPress = boolControl("Drag after long press", false)
    var tasks by remember { mutableStateOf(listOf("Write the spec", "Review the draft", "Fix the layout", "Ship it", "Celebrate")) }
    var many by remember { mutableStateOf(List(40) { "Item ${it + 1}" }) }
    var chips by remember { mutableStateOf(listOf("Kotlin", "Compose", "Swift", "Wasm")) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (layout) {
            Layout.Column -> KReorderableColumn(
                items = tasks, onReorder = { tasks = it }, key = { it }, enabled = enabled, showHandle = handle, dragAfterLongPress = longPress,
            ) { task -> KText(task, Modifier.padding(horizontal = 8.dp)) }

            Layout.Lazy -> KReorderableLazyColumn(
                items = many, onReorder = { many = it }, key = { it }, enabled = enabled, showHandle = handle, dragAfterLongPress = longPress,
                modifier = Modifier.height(320.dp),
            ) { item -> KText(item, Modifier.padding(horizontal = 8.dp)) }

            Layout.Row -> KReorderableRow(
                items = chips, onReorder = { chips = it }, key = { it }, enabled = enabled, showHandle = handle, dragAfterLongPress = longPress,
            ) { chip -> KText(chip, Modifier.padding(horizontal = 8.dp)) }
        }
        KText(
            when (layout) { Layout.Column -> tasks.joinToString(" > "); Layout.Lazy -> many.take(5).joinToString(" > ") + " ..."; Layout.Row -> chips.joinToString(" > ") },
            Modifier.fillMaxWidth(),
        )
        KText("Drag a row by its handle, or focus it and press Alt+Up / Alt+Down.")
    }
}
