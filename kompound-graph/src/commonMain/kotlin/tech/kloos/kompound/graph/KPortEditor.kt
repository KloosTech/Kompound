package tech.kloos.kompound.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.buttons.KIconButtonSize
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.PortCapacity
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.graph.model.SignalMode
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.inline.KInlineEdit
import tech.kloos.kompound.textfield.KTextField

/** A port id made from what the user typed: lower case letters, digits and `_` (`"My file"` becomes `"my_file"`); empty when nothing is left. */
public fun portIdFromName(name: String): String = name.trim().lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')

/** The words of [PortEditor]; replace them to translate it. */
public class KPortEditorLabels(
    public val addPlaceholder: String = "name",
    public val addDescription: String = "Add",
    public val removeDescription: String = "Remove",
    public val renameDescription: String = "Rename",
    public val saveDescription: String = "Save",
    public val cancelDescription: String = "Cancel",
    public val emptyName: String = "Enter a name",
    public val nameTaken: String = "Already used",
    public val showAddDescription: String = "Add",
)

/**
 * The rows of user-defined ports of a node, with the controls to change them: every port of [direction] that is not in [reserved] is shown
 * with its handle, an editable label (renaming keeps the id and the wire) and a remove button, and a field with an add button creates a new
 * one. Every change is one `UpdateNodePorts` undo step; removing a port removes its wires (undo brings them back). Rows follow
 * `KNodeScope.Input` / `Output`, so the node stays aligned with its wires.
 *
 * @param direction Edit the inputs or the outputs.
 * @param reserved Ids of the ports the node's type defines itself; they are not listed and cannot be taken by a new port.
 * @param type Type of a new port.
 * @param signal [SignalMode] of a new input.
 * @param idOf Makes the id of a new port from its name ([portIdFromName] by default).
 * @param collapsedAdd Show only a small `+` at the end until the user clicks it; then the name field appears (with a cancel button) and goes
 * away again after adding or cancelling. For nodes where the editor should take no room when idle.
 * @param labels The words.
 */
@Composable
public fun KNodeScope.PortEditor(
    direction: PortDirection,
    reserved: Set<String> = emptySet(),
    type: PortType = PortType.Any,
    signal: SignalMode = SignalMode.Latest,
    idOf: (String) -> String = ::portIdFromName,
    collapsedAdd: Boolean = false,
    labels: KPortEditorLabels = KPortEditorLabels(),
) {
    val state = LocalKGraphState.current ?: error("PortEditor must be used inside KNodeGraph")
    val current = node
    val mine = current.ports.filter { it.direction == direction && it.id.value !in reserved }
    // Handlers run later: always read the ports from the graph as it is then.
    fun ports(): List<PortSpec> = state.graph.node(current.id)?.ports.orEmpty()
    fun update(ports: List<PortSpec>) = state.execute(GraphCommand.UpdateNodePorts(current.id, ports))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (spec in mine) {
            key(spec.id) {
                val handle = @Composable {
                    PortHandle(spec.id.value, Modifier.straddleNodeEdge(direction))
                }
                val label = @Composable { modifier: Modifier ->
                    KInlineEdit(
                        spec.label, { text -> update(ports().map { if (it.id == spec.id) it.copy(label = text.ifBlank { it.id.value }) else it }) },
                        modifier, singleLine = true,
                        editContentDescription = labels.renameDescription, saveContentDescription = labels.saveDescription, cancelContentDescription = labels.cancelDescription,
                    )
                }
                val remove = @Composable {
                    KIconButton({ update(ports().filter { it.id != spec.id }) }, "${labels.removeDescription} ${spec.label}", variant = KButtonVariant.Text, size = KIconButtonSize.Small) {
                        KIcon(GraphIcons.Close, null)
                    }
                }
                if (direction == PortDirection.Input) {
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        handle(); label(Modifier.weight(1f)); remove()
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        remove(); label(Modifier.weight(1f)); handle()
                    }
                }
            }
        }
        var name by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var adding by remember { mutableStateOf(!collapsedAdd) }
        fun add() {
            val id = idOf(name)
            error = when {
                id.isEmpty() -> labels.emptyName
                id in reserved || ports().any { it.id.value == id } -> labels.nameTaken
                else -> null
            }
            if (error != null) return
            val spec = if (direction == PortDirection.Input) PortSpec(PortId(id), PortDirection.Input, name.trim(), type, PortCapacity.One, signal)
            else PortSpec(PortId(id), PortDirection.Output, name.trim(), type)
            update(ports() + spec)
            name = ""
            if (collapsedAdd) adding = false
        }
        if (!adding) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                KIconButton({ adding = true }, labels.showAddDescription, variant = KButtonVariant.Tonal, size = KIconButtonSize.Small) { KIcon(GraphIcons.Add, null) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
                KTextField(name, { name = it; error = null }, Modifier.weight(1f), placeholder = labels.addPlaceholder, isError = error != null, supportingText = error)
                KIconButton(::add, labels.addDescription, variant = KButtonVariant.Tonal, size = KIconButtonSize.Small) { KIcon(GraphIcons.Add, null) }
                if (collapsedAdd) {
                    KIconButton({ adding = false; name = ""; error = null }, labels.cancelDescription, variant = KButtonVariant.Text, size = KIconButtonSize.Small) { KIcon(GraphIcons.Close, null) }
                }
            }
        }
    }
}
