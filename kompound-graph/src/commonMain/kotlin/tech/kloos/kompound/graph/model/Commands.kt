package tech.kloos.kompound.graph.model

import androidx.compose.ui.geometry.Offset

/**
 * An edit of a [Graph]. Every command can be applied and knows how to undo itself, which is what the editor's
 * undo and redo are built on. Commands are plain data, so they can also be logged, replayed or sent over the wire.
 */
public sealed interface GraphCommand {
    /** Adds [node] (or replaces the node with its id). */
    public data class AddNode(public val node: GraphNode) : GraphCommand

    /** Removes [ids] and all edges touching them. */
    public data class RemoveNodes(public val ids: Set<NodeId>) : GraphCommand

    /** Moves nodes by the given deltas in world units. */
    public data class MoveNodes(public val deltas: Map<NodeId, Offset>) : GraphCommand

    /** Puts nodes at exact positions; the inverse of [MoveNodes] (adding and subtracting floats is not exact). */
    public data class PlaceNodes(public val positions: Map<NodeId, Offset>) : GraphCommand

    /** Adds [edge]. */
    public data class Connect(public val edge: Edge) : GraphCommand

    /** Removes the edges [ids]. */
    public data class Disconnect(public val ids: Set<EdgeId>) : GraphCommand

    /** Replaces the payload of a node. */
    public data class UpdateNodeData(public val id: NodeId, public val data: Any?) : GraphCommand

    /** Creates the group or replaces its title, colour and collapsed state. Members are set with [AssignGroups]. */
    public data class PutGroup(public val group: NodeGroup) : GraphCommand

    /** Removes the group [id]; its members stay in the graph, outside any group. */
    public data class RemoveGroup(public val id: GroupId) : GraphCommand

    /** Puts nodes into groups, or out of any group with `null`. */
    public data class AssignGroups(public val assignments: Map<NodeId, GroupId?>) : GraphCommand

    /** Several commands applied in order and undone as one step. */
    public data class Batch(public val commands: List<GraphCommand>, public val label: String? = null) : GraphCommand
}

/** The outcome of [GraphCommand.applyTo]: the new graph and the command that restores the old one. */
public class AppliedCommand(public val graph: Graph, public val inverse: GraphCommand)

/** Applies this command to [graph]; returns `null` when it changes nothing (unknown ids, no-op), so it is not recorded as an undo step. */
public fun GraphCommand.applyTo(graph: Graph): AppliedCommand? = when (this) {
    is GraphCommand.AddNode -> {
        val before = graph.node(node.id)
        val restoreEdges = if (before != null) graph.edgesOf(node.id) else emptyList()
        val after = graph.withNode(node)
        if (after == graph) null
        else AppliedCommand(
            after,
            if (before == null) GraphCommand.RemoveNodes(setOf(node.id))
            else GraphCommand.Batch(listOf(GraphCommand.AddNode(before)) + restoreEdges.map { GraphCommand.Connect(it) }),
        )
    }
    is GraphCommand.RemoveNodes -> {
        // everything inside a removed subgraph goes too; restoring must add the enclosing subgraph node before what is inside it
        val all = ids + graph.descendantsOf(ids)
        val removed = all.mapNotNull { graph.node(it) }.sortedBy { graph.depthOf(it.id) }
        if (removed.isEmpty()) null
        else {
            val touching = removed.flatMap { graph.edgesOf(it.id) }.distinctBy { it.id }
            AppliedCommand(
                graph.withoutNodes(ids),
                GraphCommand.Batch(removed.map { GraphCommand.AddNode(it) } + touching.map { GraphCommand.Connect(it) }),
            )
        }
    }
    is GraphCommand.MoveNodes -> {
        val present = deltas.filterKeys { graph.node(it) != null }.filterValues { it != Offset.Zero }
        if (present.isEmpty()) null
        else AppliedCommand(graph.withMoved(present), GraphCommand.PlaceNodes(present.mapValues { graph.node(it.key)!!.position }))
    }
    is GraphCommand.PlaceNodes -> {
        val present = positions.filterKeys { graph.node(it) != null }
        val old = present.mapValues { graph.node(it.key)!!.position }
        val changed = present.filter { (id, p) -> old[id] != p }
        if (changed.isEmpty()) null
        else AppliedCommand(graph.withPlaced(changed), GraphCommand.PlaceNodes(changed.mapValues { old.getValue(it.key) }))
    }
    is GraphCommand.Connect -> {
        val after = graph.withEdge(edge)
        if (after == graph) null else AppliedCommand(after, GraphCommand.Disconnect(setOf(edge.id)))
    }
    is GraphCommand.Disconnect -> {
        val removed = ids.mapNotNull { graph.edge(it) }
        if (removed.isEmpty()) null else AppliedCommand(graph.withoutEdges(ids), GraphCommand.Batch(removed.map { GraphCommand.Connect(it) }))
    }
    is GraphCommand.UpdateNodeData -> {
        val node = graph.node(id)
        if (node == null || node.data == data) null
        else AppliedCommand(graph.withNode(node.copy(data = data)), GraphCommand.UpdateNodeData(id, node.data))
    }
    is GraphCommand.PutGroup -> {
        val before = graph.group(group.id)
        val after = graph.withGroup(group)
        if (after == graph) null else AppliedCommand(after, if (before == null) GraphCommand.RemoveGroup(group.id) else GraphCommand.PutGroup(before))
    }
    is GraphCommand.RemoveGroup -> {
        val before = graph.group(id)
        if (before == null) null
        else AppliedCommand(
            graph.withoutGroup(id),
            GraphCommand.Batch(listOf(GraphCommand.PutGroup(before), GraphCommand.AssignGroups(graph.membersOf(id).associateWith { id }))),
        )
    }
    is GraphCommand.AssignGroups -> {
        val effective = assignments.filter { (nid, g) -> graph.node(nid) != null && (g == null || graph.group(g) != null) && graph.node(nid)!!.group != g }
        if (effective.isEmpty()) null
        else AppliedCommand(graph.withAssigned(effective), GraphCommand.AssignGroups(effective.mapValues { graph.node(it.key)!!.group }))
    }
    is GraphCommand.Batch -> {
        var current = graph
        val inverses = ArrayList<GraphCommand>()
        for (c in commands) c.applyTo(current)?.let { current = it.graph; inverses += it.inverse }
        if (inverses.isEmpty()) null else AppliedCommand(current, GraphCommand.Batch(inverses.asReversed().toList(), label))
    }
}
