package tech.kloos.kompound.graph.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.NodeId
import kotlin.math.max

/**
 * Tuning of [GraphLayout.layered].
 *
 * @property layerSpacing Horizontal gap between columns of nodes (world units).
 * @property nodeSpacing Vertical gap between nodes in a column.
 * @property componentSpacing Vertical gap between groups of nodes that are not connected to each other.
 * @property sweeps Passes of the crossing-reduction heuristic; more passes can untangle larger graphs a little further.
 * @property fallbackSize Size assumed for nodes without a measured size.
 */
public class LayoutOptions(
    public val layerSpacing: Float = 120f,
    public val nodeSpacing: Float = 40f,
    public val componentSpacing: Float = 80f,
    public val sweeps: Int = 8,
    public val fallbackSize: Size = Size(220f, 120f),
)

/**
 * Automatic arrangement of a graph into columns following the direction of the wires (a layered, "Sugiyama style" layout):
 * sources on the left, sinks on the right, nodes ordered inside each column to cut down wire crossings, columns of connected nodes
 * pulled towards the vertical middle of their neighbours, and unrelated parts of the graph stacked below one another.
 *
 * It is a pure function: deterministic for equal input, no Compose, no side effects. Loops in the graph are tolerated: wires that
 * would point backwards are ignored for the column assignment.
 */
public object GraphLayout {
    /**
     * Computes new top-left positions for [nodes] (default: every node of [graph]). Only wires between those nodes count. The
     * result keeps the top-left corner of the area the nodes occupied before, so the layout stays where the user was working.
     *
     * @param sizes Measured node sizes in world units; missing entries use [LayoutOptions.fallbackSize].
     */
    public fun layered(
        graph: Graph,
        sizes: Map<NodeId, Size> = emptyMap(),
        nodes: Set<NodeId> = graph.nodes.keys,
        options: LayoutOptions = LayoutOptions(),
    ): Map<NodeId, Offset> {
        val ids = graph.nodes.values.filter { it.id in nodes }.sortedWith(compareBy({ it.position.x }, { it.position.y }, { it.id.value })).map { it.id }
        if (ids.isEmpty()) return emptyMap()
        val index = HashMap<NodeId, Int>().also { m -> ids.forEachIndexed { i, id -> m[id] = i } }
        val size = Array(ids.size) { sizes[ids[it]] ?: options.fallbackSize }

        // Wires between the chosen nodes, as index pairs, without self loops and duplicates.
        val pairs = LinkedHashSet<Long>()
        for (e in graph.edges.values) {
            val a = index[e.from.node] ?: continue
            val b = index[e.to.node] ?: continue
            if (a != b) pairs += a.toLong() shl 32 or b.toLong()
        }
        val out = Array(ids.size) { ArrayList<Int>() }
        val inc = Array(ids.size) { ArrayList<Int>() }
        for (p in pairs) { val a = (p shr 32).toInt(); val b = (p and 0xffffffffL).toInt(); out[a].add(b); inc[b].add(a) }

        // Connected components (ignoring direction), ordered by their first node.
        val component = IntArray(ids.size) { -1 }
        var components = 0
        for (start in ids.indices) {
            if (component[start] != -1) continue
            val stack = ArrayList<Int>().also { it.add(start) }
            component[start] = components
            while (stack.isNotEmpty()) {
                val n = stack.removeAt(stack.lastIndex)
                for (m in out[n] + inc[n]) if (component[m] == -1) { component[m] = components; stack.add(m) }
            }
            components++
        }

        val result = arrayOfNulls<Offset>(ids.size)
        var cursorY = 0f
        for (c in 0 until components) {
            val members = ids.indices.filter { component[it] == c }
            val placed = layoutComponent(members, out, inc, size, options)
            val top = placed.values.minOf { it.y }
            val bottom = placed.entries.maxOf { it.value.y + size[it.key].height }
            for ((n, p) in placed) result[n] = Offset(p.x, p.y - top + cursorY)
            cursorY += bottom - top + options.componentSpacing
        }

        // Keep the layout where the nodes were: align the new top-left with the old one.
        val oldLeft = ids.minOf { graph.node(it)!!.position.x }
        val oldTop = ids.minOf { graph.node(it)!!.position.y }
        val newLeft = result.filterNotNull().minOf { it.x }
        val newTop = result.filterNotNull().minOf { it.y }
        return ids.indices.associate { i -> ids[i] to Offset(result[i]!!.x - newLeft + oldLeft, result[i]!!.y - newTop + oldTop) }
    }

    private fun layoutComponent(
        members: List<Int>,
        out: Array<ArrayList<Int>>,
        inc: Array<ArrayList<Int>>,
        size: Array<Size>,
        options: LayoutOptions,
    ): Map<Int, Offset> {
        val inSet = members.toHashSet()

        // 1. Break loops: a depth-first walk marks the wires that close a loop; they are ignored below.
        val state = HashMap<Int, Int>()          // 1 = on the current path, 2 = done
        val back = HashSet<Long>()
        for (root in members.sortedBy { inc[it].count { p -> p in inSet } }) {
            if (state[root] != null) continue
            val stack = ArrayList<IntArray>().also { it.add(intArrayOf(root, 0)) }
            state[root] = 1
            while (stack.isNotEmpty()) {
                val top = stack.last()
                val n = top[0]
                if (top[1] < out[n].size) {
                    val m = out[n][top[1]++]
                    if (m !in inSet) continue
                    when (state[m]) {
                        1 -> back += n.toLong() shl 32 or m.toLong()
                        null -> { state[m] = 1; stack.add(intArrayOf(m, 0)) }
                    }
                } else { state[n] = 2; stack.removeAt(stack.lastIndex) }
            }
        }
        fun forward(a: Int, b: Int) = (a.toLong() shl 32 or b.toLong()) !in back
        val preds = members.associateWith { n -> inc[n].filter { it in inSet && forward(it, n) } }
        val succs = members.associateWith { n -> out[n].filter { it in inSet && forward(n, it) } }

        // 2. Columns: longest path from the sources.
        val layer = HashMap<Int, Int>()
        val remaining = HashMap<Int, Int>().also { m -> members.forEach { m[it] = preds.getValue(it).size } }
        val queue = ArrayDeque(members.filter { remaining.getValue(it) == 0 })
        queue.forEach { layer[it] = 0 }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            for (m in succs.getValue(n)) {
                layer[m] = max(layer[m] ?: 0, layer.getValue(n) + 1)
                remaining[m] = remaining.getValue(m) - 1
                if (remaining.getValue(m) == 0) queue.addLast(m)
            }
        }
        val columnCount = (layer.values.maxOrNull() ?: 0) + 1
        val columns = MutableList(columnCount) { c -> members.filter { layer[it] == c }.toMutableList() }

        // 3. Order inside the columns: barycentre sweeps down and up (stable, so equal keys keep their order).
        fun rank(): Map<Int, Int> = HashMap<Int, Int>().also { m -> columns.forEach { col -> col.forEachIndexed { i, n -> m[n] = i } } }
        repeat(options.sweeps) { sweep ->
            val down = sweep % 2 == 0
            val range = if (down) 1 until columnCount else (columnCount - 2) downTo 0
            for (c in range) {
                val ranks = rank()
                val neighbours = if (down) preds else succs
                val keyed = columns[c].map { n ->
                    val ns = neighbours.getValue(n)
                    n to (if (ns.isEmpty()) ranks.getValue(n).toDouble() else ns.map { ranks.getValue(it) }.average())
                }
                columns[c] = keyed.sortedBy { it.second }.map { it.first }.toMutableList()
            }
        }

        // 4. Coordinates: columns left to right; inside a column, stack and pull towards the neighbours' middle.
        val columnX = FloatArray(columnCount)
        for (c in 1 until columnCount) columnX[c] = columnX[c - 1] + (columns[c - 1].maxOfOrNull { size[it].width } ?: 0f) + options.layerSpacing
        val y = HashMap<Int, Float>()
        for (col in columns) { var cursor = 0f; for (n in col) { y[n] = cursor; cursor += size[n].height + options.nodeSpacing } }
        repeat(options.sweeps) { pass ->
            val down = pass % 2 == 0
            val order = if (down) 0 until columnCount else (columnCount - 1) downTo 0
            for (c in order) {
                val neighbours = if (down) preds else succs
                var cursor = Float.NEGATIVE_INFINITY
                for (n in columns[c]) {
                    val ns = neighbours.getValue(n)
                    val wanted = if (ns.isEmpty()) y.getValue(n) else ns.map { y.getValue(it) + size[it].height / 2f }.average().toFloat() - size[n].height / 2f
                    val placed = max(wanted, cursor)
                    y[n] = placed
                    cursor = placed + size[n].height + options.nodeSpacing
                }
            }
        }
        return members.associateWith { Offset(columnX[layer.getValue(it)], y.getValue(it)) }
    }
}
