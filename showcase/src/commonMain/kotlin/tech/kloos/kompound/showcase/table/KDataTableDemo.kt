package tech.kloos.kompound.showcase.table

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.table.KColumn
import tech.kloos.kompound.table.KColumnWidth
import tech.kloos.kompound.table.KDataTable
import tech.kloos.kompound.table.KSortState

private const val Usage_table = """import tech.kloos.kompound.table.KColumn
import tech.kloos.kompound.table.KColumnWidth
import tech.kloos.kompound.table.KDataTable
import tech.kloos.kompound.table.KSortState

var sort by remember { mutableStateOf<KSortState?>(null) }
var selection by remember { mutableStateOf(emptySet<Any>()) }

KDataTable(
    rows = people,
    columns = listOf(
        KColumn.text("name", "Name") { it.name },
        KColumn.text("age", "Age", width = KColumnWidth.Fixed(80.dp), alignment = Alignment.End,
            comparator = compareBy { it.age }) { it.age.toString() },
    ),
    rowKey = { it.id },
    modifier = Modifier.height(320.dp), // bounded height: rows scroll inside
    sort = sort, onSortChange = { sort = it },
    selection = selection, onSelectionChange = { selection = it },
)"""

private class Person(val id: Int, val name: String, val city: String, val age: Int)

private val Cities = listOf("Berlin", "Lisbon", "Oslo", "Lyon", "Turin", "Porto")

@KompoundDemo(
    id = "table.data",
    title = "KDataTable",
    description = "A sortable, selectable table that only composes the rows on screen, so thousands of rows stay smooth.",
    category = KompoundCategory.Data,
    tags = ["table", "grid", "sort", "virtualized", "selection", "list"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_table,
)
@Composable
fun DemoScope.KDataTableDemo() {
    val count = floatControl("Rows", 0f..10000f, 2000f).toInt()
    val selectable = boolControl("Selectable", true)
    val striped = boolControl("Striped", true)
    val loading = boolControl("Loading", false)
    val rows = remember(count) { List(count) { Person(it, "Person ${it + 1}", Cities[(it * 7) % Cities.size], 18 + (it * 13) % 60) } }
    var sort by remember { mutableStateOf<KSortState?>(null) }
    var selection by remember { mutableStateOf(emptySet<Any>()) }
    val columns = remember {
        listOf(
            KColumn.text<Person>("name", "Name") { it.name },
            KColumn.text<Person>("city", "City") { it.city },
            KColumn.text<Person>("age", "Age", KColumnWidth.Fixed(80.dp), alignment = Alignment.End, comparator = compareBy { it.age }) { it.age.toString() },
        )
    }
    KDataTable(
        rows, columns, { it.id }, Modifier.fillMaxWidth().height(360.dp).padding(16.dp),
        sort = sort, onSortChange = { sort = it },
        selection = if (selectable) selection else null, onSelectionChange = if (selectable) { { selection = it } } else null,
        striped = striped, loading = loading,
    )
}
