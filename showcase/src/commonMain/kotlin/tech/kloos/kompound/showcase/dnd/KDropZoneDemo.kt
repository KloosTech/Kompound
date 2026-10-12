package tech.kloos.kompound.showcase.dnd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
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
import tech.kloos.kompound.dnd.KDragAndDropContainer
import tech.kloos.kompound.dnd.KDraggable
import tech.kloos.kompound.dnd.KDropZone
import tech.kloos.kompound.dnd.rememberKDragAndDropState
import tech.kloos.kompound.text.KText

private const val Usage_dropzone = """import tech.kloos.kompound.dnd.*

val dnd = rememberKDragAndDropState<Card>()

KDragAndDropContainer(dnd) {
    Row {
        columns.forEach { column ->
            KDropZone(key = column.id, state = dnd, onDrop = { card -> move(card, to = column) }) { hovered ->
                column.cards.forEach { card ->
                    KDraggable(key = card.id, data = card, state = dnd) { KText(card.title) }
                }
            }
        }
    }
}"""

private data class Card(val id: String, val title: String)

@KompoundDemo(
    id = "dnd.dropzone",
    title = "KDraggable and KDropZone",
    description = "Drag cards between zones, for boards, trays and inboxes; zones highlight when they accept the item.",
    category = KompoundCategory.Layout,
    tags = ["drag", "drop", "kanban", "board", "target", "zone"],
    since = "0.2.0",
    status = KompoundStatus.Beta,
    usage = Usage_dropzone,
)
@Composable
fun DemoScope.KDropZoneDemo() {
    val doneOnlyReviewed = boolControl("Done accepts only reviewed cards", false)
    val longPress = boolControl("Drag after long press", false)
    var columns by remember {
        mutableStateOf(
            mapOf(
                "todo" to listOf(Card("a", "Write spec"), Card("b", "Design API"), Card("c", "Reviewed: demo")),
                "doing" to listOf(Card("d", "Reviewed: tests")),
                "done" to emptyList(),
            ),
        )
    }
    fun acceptedBy(column: String, card: Card) = column != "done" || !doneOnlyReviewed || card.title.startsWith("Reviewed")
    /** Puts [card] in [column] before [before], or at the end. */
    fun place(card: Card, column: String, before: Card? = null) {
        columns = columns.mapValues { (id, cards) ->
            val rest = cards.filter { it.id != card.id }
            if (id != column) rest else {
                val at = before?.let { b -> rest.indexOfFirst { it.id == b.id } }?.takeIf { it >= 0 } ?: rest.size
                rest.take(at) + card + rest.drop(at)
            }
        }
    }
    val dnd = rememberKDragAndDropState<Card>(dragAfterLongPress = longPress)
    KDragAndDropContainer(dnd, Modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("todo" to "To do", "doing" to "Doing", "done" to "Done").forEach { (id, title) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText(title)
                    KDropZone(
                        key = id, state = dnd, onDrop = { card -> if (columns.getValue(id).none { it.id == card.id }) place(card, id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        accepts = { card -> acceptedBy(id, card) },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            columns.getValue(id).forEach { card ->
                                KDraggable(
                                    key = card.id, data = card, state = dnd, modifier = Modifier.fillMaxWidth(),
                                    onItemOver = { held -> place(held, id, before = card) }, accepts = { held -> acceptedBy(id, held) },
                                ) { KText(card.title) }
                            }
                        }
                    }
                }
            }
        }
    }
}
