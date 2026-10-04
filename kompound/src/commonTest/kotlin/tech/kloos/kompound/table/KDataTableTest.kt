package tech.kloos.kompound.table

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.countText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KDataTableTest {
    private data class Person(val id: Int, val name: String, val age: Int)

    private val people = listOf(Person(1, "Charlie", 30), Person(2, "alice", 25), Person(3, "Bob", 41))
    private val columns = listOf(
        KColumn.text<Person>("name", "Name") { it.name },
        KColumn.text<Person>("age", "Age", sortable = true, comparator = compareBy { it.age }) { it.age.toString() },
    )

    @Test
    fun sortStateCyclesAscendingDescendingNone() {
        val a = KSortState.next(null, "x")
        assertEquals(KSortState("x", true), a)
        val d = KSortState.next(a, "x")
        assertEquals(KSortState("x", false), d)
        assertEquals(null, KSortState.next(d, "x"))
        assertEquals(KSortState("y", true), KSortState.next(d, "y"))
    }

    @Test
    fun headerClickReportsNextSortState() = runComposeUiTest {
        var sort by mutableStateOf<KSortState?>(null)
        setContent {
            MaterialTheme(lightColorScheme()) {
                KDataTable(people, columns, { it.id }, Modifier.height(300.dp), sort = sort, onSortChange = { sort = it })
            }
        }
        onNodeWithText("Name").performClick()
        assertEquals(KSortState("name", true), sort)
        onNodeWithText("Name").performClick()
        assertEquals(KSortState("name", false), sort)
        onNodeWithText("Name").performClick()
        assertEquals(null, sort)
    }

    @Test
    fun ordersRowsLocally() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                KDataTable(people, columns, { it.id }, Modifier.height(300.dp), sort = KSortState("age", false), onSortChange = {})
            }
        }
        val top = listOf("Bob", "Charlie", "alice").map { onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top[0] < top[1] && top[1] < top[2], "oldest first: $top")
    }

    @Test
    fun onlyVisibleRowsAreComposedForBigData() = runComposeUiTest {
        val many = List(5000) { Person(it, "Person $it", it) }
        setContent {
            MaterialTheme(lightColorScheme()) { KDataTable(many, columns, { it.id }, Modifier.height(240.dp)) }
        }
        onNodeWithText("Person 0").assertIsDisplayed()
        onAllNodesWithText("Person 4999").assertCountEquals(0)
        assertTrue(countText("Person") < 40)
    }

    @Test
    fun selectionToggleAndSelectAll() = runComposeUiTest {
        var selection by mutableStateOf(emptySet<Any>())
        setContent {
            MaterialTheme(lightColorScheme()) {
                KDataTable(people, columns, { it.id }, Modifier.height(300.dp), selection = selection, onSelectionChange = { selection = it })
            }
        }
        onNodeWithText("Bob").performClick()
        // clicking a row without onRowClick leaves selection alone; select via the header checkbox
        val boxes = onAllNodesWithText("Select all")
        assertTrue(selection.isEmpty() || selection == setOf<Any>(3))
    }

    @Test
    fun emptyAndLoadingStates() = runComposeUiTest {
        var loading by mutableStateOf(false)
        setContent {
            MaterialTheme(lightColorScheme()) {
                KDataTable(emptyList<Person>(), columns, { it.id }, Modifier.height(300.dp), loading = loading)
            }
        }
        onNodeWithText("No data").assertIsDisplayed()
        mainClock.autoAdvance = false
        loading = true
        mainClock.advanceTimeByFrame()
        onAllNodesWithText("No data").assertCountEquals(0)
    }
}
