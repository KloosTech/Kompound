package tech.kloos.kompound.graph.serialization

import androidx.compose.ui.geometry.Offset
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KViewportState
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.GroupId
import tech.kloos.kompound.graph.model.NodeGroup
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortCapacity
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.graph.model.SignalMode

/**
 * Turns a node's [GraphNode.data] into JSON and back. Register one per node kind in [GraphJson]; the codec belongs to the code that
 * defines the kind (with its composable), so a file never needs to know about composables.
 */
public interface NodeDataCodec {
    /** The JSON for [data] (never called with `null`). */
    public fun encode(data: Any): JsonValue

    /** The data object for [json] (never called with `JsonNull`). */
    public fun decode(json: JsonValue): Any
}

/** A [NodeDataCodec] from two lambdas. */
public fun <T : Any> nodeDataCodec(encode: (T) -> JsonValue, decode: (JsonValue) -> T): NodeDataCodec = object : NodeDataCodec {
    @Suppress("UNCHECKED_CAST")
    override fun encode(data: Any): JsonValue = encode(data as T)
    override fun decode(json: JsonValue): Any = decode(json)
}

/** A graph read from JSON, with the viewport that was saved next to it (if any). */
public class LoadedGraph(public val graph: Graph, public val offset: Offset?, public val zoom: Float?)

/**
 * Saves graphs as JSON text and loads them again, without any dependency.
 *
 * Everything the model holds is written (nodes with ports, data, group and scope; edges; groups) plus an optional viewport. `data` is
 * written by the codec registered for the node's kind in [nodeData]; without one, `null`, booleans, strings, numbers (kept as `Int`,
 * `Long`, `Float` or `Double`) and [JsonValue] are handled, and any other type makes [encode] fail with a [GraphJsonException]. On loading,
 * a kind without a codec gets plain scalars back, and objects and arrays as [JsonValue], which [encode] writes back unchanged: a file with
 * node kinds this app does not know survives a load and save intact.
 *
 * Port types are stored by id; ids found in [portTypes] come back as those objects (custom `accepts` rules), others as `PortType.of(id)`.
 *
 * @property nodeData Codec per node kind.
 * @property portTypes The port types of your app.
 * @property pretty Indent the output.
 * @property migrate Applied to every node as it is loaded, so a new app version can fix up nodes saved by an older one (new ports, a changed
 * [tech.kloos.kompound.graph.model.SignalMode], renamed kinds). Edges are checked after it ran. When it renames a kind and leaves the data alone, the
 * data is read again with the new kind's `nodeData` codec (it was first read with the old kind's, or kept as raw JSON).
 * @property values How pinned output values are written (see [ValueJson]); pass one with your [ValueCodec]s if pins hold your own types.
 */
public class GraphJson(
    private val nodeData: Map<String, NodeDataCodec> = emptyMap(),
    portTypes: Collection<PortType> = emptyList(),
    private val pretty: Boolean = true,
    private val values: ValueJson = ValueJson(),
    private val migrate: (GraphNode) -> GraphNode = { it },
) {
    private val types: Map<String, PortType> = portTypes.associateBy { it.id }

    /** The JSON text of [graph]; pass [viewport] to save the view with it. */
    public fun encode(graph: Graph, viewport: KViewportState? = null): String = encodeToValue(graph, viewport).toJson(pretty)

    /** Like [encode] but returns the tree. */
    public fun encodeToValue(graph: Graph, viewport: KViewportState? = null): JsonObject = jsonObjectOf(
        "format" to JsonString(Format),
        "version" to JsonNumber(Version.toDouble()),
        "nodes" to JsonArray(graph.nodes.values.map(::nodeToJson)),
        "edges" to JsonArray(graph.edges.values.map(::edgeToJson)),
        "groups" to JsonArray(graph.groups.values.map(::groupToJson)),
        "viewport" to viewport?.let {
            jsonObjectOf("x" to num(it.offset.x), "y" to num(it.offset.y), "zoom" to num(it.zoom))
        },
    )

    /** Reads [text]; throws [GraphJsonException] when it is not JSON, not a Kompound graph, from a newer version, or inconsistent. */
    public fun decode(text: String): LoadedGraph = decodeFromValue(JsonValue.parse(text))

    /** Like [decode] for a parsed tree. */
    public fun decodeFromValue(json: JsonValue): LoadedGraph {
        val root = json as? JsonObject ?: throw GraphJsonException("A graph file is a JSON object")
        if (string(root, "format", "graph") != Format) throw GraphJsonException("Not a Kompound graph (format is not \"$Format\")")
        val version = int(root, "version", "graph")
        if (version < 1 || version > Version) throw GraphJsonException("Graph version $version is not supported (this app reads up to $Version)")
        val nodes = array(root, "nodes", "graph").mapIndexed { i, v ->
            val at = "nodes[$i]"
            val raw = obj(v, at)
            val before = nodeFromJson(raw, at)
            val after = migrate(before)
            // A renamed kind: its data was read with the old kind's codec (or kept raw). If migrate left the data alone, read it again with
            // the codec of the new kind, so renaming a kind that has typed data works.
            val codec = nodeData[after.kind]
            val rawData = raw["data"]?.takeUnless { it == JsonNull }
            if (after.kind != before.kind && codec != null && rawData != null && after.data == before.data) after.copy(data = codec.decode(rawData)) else after
        }
        val ids = HashSet<NodeId>()
        for (n in nodes) if (!ids.add(n.id)) throw GraphJsonException("Duplicate node id \"${n.id}\"")
        val edges = array(root, "edges", "graph").mapIndexed { i, v -> edgeFromJson(obj(v, "edges[$i]"), "edges[$i]") }
        val groups = (root["groups"] as? JsonArray)?.items.orEmpty().mapIndexed { i, v -> groupFromJson(obj(v, "groups[$i]"), "groups[$i]") }
        val byId = nodes.associateBy { it.id }
        val seenEdges = HashSet<EdgeId>()
        for (e in edges) {
            if (!seenEdges.add(e.id)) throw GraphJsonException("Duplicate edge id \"${e.id}\"")
            for (ref in listOf(e.from, e.to)) {
                if (byId[ref.node]?.port(ref.port) == null) throw GraphJsonException("Edge \"${e.id}\" uses the missing port $ref")
            }
        }
        val groupIds = groups.map { it.id }.toSet()
        for (n in nodes) {
            if (n.group != null && n.group !in groupIds) throw GraphJsonException("Node \"${n.id}\" is in the missing group \"${n.group}\"")
            if (n.scope != null && n.scope !in byId) throw GraphJsonException("Node \"${n.id}\" is inside the missing node \"${n.scope}\"")
        }
        val view = root["viewport"] as? JsonObject
        return LoadedGraph(
            Graph.of(nodes, edges, groups),
            view?.let { Offset(float(it, "x", "viewport"), float(it, "y", "viewport")) },
            view?.let { float(it, "zoom", "viewport") },
        )
    }

    // --- nodes --------------------------------------------------------------------------------------------

    private fun nodeToJson(node: GraphNode): JsonValue {
        val data = node.data
        var dataType: String? = null
        val dataJson: JsonValue? = when {
            data == null -> null
            nodeData[node.kind] != null -> nodeData.getValue(node.kind).encode(data)
            data is JsonValue -> data
            data is Boolean -> JsonBool(data)
            data is String -> JsonString(data)
            data is Int -> { dataType = "int"; JsonNumber(data.toDouble()) }
            data is Long -> { dataType = "long"; JsonNumber(data.toDouble()) }
            data is Float -> { dataType = "float"; JsonNumber(data.toString().toDouble()) }
            data is Double -> JsonNumber(data)
            else -> throw GraphJsonException("Node \"${node.id}\" (kind \"${node.kind}\") has data of type ${data::class.simpleName}: register a NodeDataCodec for the kind")
        }
        return jsonObjectOf(
            "id" to JsonString(node.id.value),
            "kind" to JsonString(node.kind),
            "x" to num(node.position.x),
            "y" to num(node.position.y),
            "ports" to JsonArray(node.ports.map(::portToJson)),
            "data" to dataJson,
            "dataType" to dataType?.let { JsonString(it) },
            "pin" to node.pin?.let { pin -> JsonObject(pin.entries.associate { (port, v) -> port.value to values.encode(v) }) },
            "group" to node.group?.let { JsonString(it.value) },
            "scope" to node.scope?.let { JsonString(it.value) },
        )
    }

    private fun nodeFromJson(o: JsonObject, at: String): GraphNode {
        val kind = string(o, "kind", at)
        val raw = o["data"]?.takeUnless { it == JsonNull }
        val data: Any? = when {
            raw == null -> null
            nodeData[kind] != null -> nodeData.getValue(kind).decode(raw)
            else -> when ((o["dataType"] as? JsonString)?.value) {
                "int" -> (raw as? JsonNumber)?.value?.toInt()
                "long" -> (raw as? JsonNumber)?.value?.toLong()
                "float" -> (raw as? JsonNumber)?.value?.toFloat()
                else -> when (raw) {
                    is JsonBool -> raw.value
                    is JsonString -> raw.value
                    is JsonNumber -> raw.value
                    else -> raw
                }
            }
        }
        return GraphNode(
            id = NodeId(string(o, "id", at)),
            kind = kind,
            position = Offset(float(o, "x", at), float(o, "y", at)),
            ports = array(o, "ports", at).mapIndexed { i, v -> portFromJson(obj(v, "$at.ports[$i]"), "$at.ports[$i]") },
            data = data,
            pin = (o["pin"] as? JsonObject)?.fields?.entries?.associate { (port, v) -> PortId(port) to values.decode(v) },
            group = (o["group"] as? JsonString)?.let { GroupId(it.value) },
            scope = (o["scope"] as? JsonString)?.let { NodeId(it.value) },
        )
    }

    private fun portToJson(p: PortSpec): JsonValue = jsonObjectOf(
        "id" to JsonString(p.id.value),
        "direction" to JsonString(p.direction.name),
        "label" to JsonString(p.label),
        "type" to JsonString(p.type.id),
        "capacity" to JsonString(p.capacity.name),
        "signal" to p.signal.takeIf { it != SignalMode.Latest }?.let { JsonString(it.name) },
    )

    private fun portFromJson(o: JsonObject, at: String): PortSpec {
        val direction = PortDirection.entries.firstOrNull { it.name == string(o, "direction", at) } ?: throw GraphJsonException("$at.direction is not Input or Output")
        val id = string(o, "id", at)
        val typeId = (o["type"] as? JsonString)?.value ?: PortType.Any.id
        val capacity = (o["capacity"] as? JsonString)?.let { c -> PortCapacity.entries.firstOrNull { it.name == c.value } ?: throw GraphJsonException("$at.capacity is not One or Many") }
        return PortSpec(
            PortId(id), direction,
            (o["label"] as? JsonString)?.value ?: id,
            types[typeId] ?: PortType.of(typeId),
            capacity ?: if (direction == PortDirection.Input) PortCapacity.One else PortCapacity.Many,
            (o["signal"] as? JsonString)?.let { m -> SignalMode.entries.firstOrNull { it.name == m.value } ?: throw GraphJsonException("$at.signal is not a signal mode") } ?: SignalMode.Latest,
        )
    }

    // --- edges and groups ---------------------------------------------------------------------------------

    private fun edgeToJson(e: Edge): JsonValue = jsonObjectOf(
        "id" to JsonString(e.id.value),
        "from" to refToJson(e.from),
        "to" to refToJson(e.to),
    )

    private fun refToJson(r: PortRef): JsonValue = jsonObjectOf("node" to JsonString(r.node.value), "port" to JsonString(r.port.value))

    private fun edgeFromJson(o: JsonObject, at: String): Edge = Edge(
        EdgeId(string(o, "id", at)),
        refFromJson(obj(o["from"], "$at.from"), "$at.from"),
        refFromJson(obj(o["to"], "$at.to"), "$at.to"),
    )

    private fun refFromJson(o: JsonObject, at: String) = PortRef(NodeId(string(o, "node", at)), PortId(string(o, "port", at)))

    private fun groupToJson(g: NodeGroup): JsonValue = jsonObjectOf(
        "id" to JsonString(g.id.value),
        "title" to JsonString(g.title),
        "collapsed" to JsonBool(g.collapsed),
        "color" to num(g.color.toFloat()),
    )

    private fun groupFromJson(o: JsonObject, at: String): NodeGroup = NodeGroup(
        GroupId(string(o, "id", at)),
        (o["title"] as? JsonString)?.value ?: "Group",
        (o["collapsed"] as? JsonBool)?.value ?: false,
        (o["color"] as? JsonNumber)?.value?.toInt() ?: 0,
    )

    // --- helpers ------------------------------------------------------------------------------------------

    private fun num(f: Float): JsonNumber = JsonNumber(f.toString().toDouble())

    private fun obj(v: JsonValue?, at: String): JsonObject = v as? JsonObject ?: throw GraphJsonException("$at must be an object")

    private fun string(o: JsonObject, name: String, at: String): String =
        (o[name] as? JsonString)?.value ?: throw GraphJsonException("$at.$name must be a string")

    private fun float(o: JsonObject, name: String, at: String): Float =
        (o[name] as? JsonNumber)?.value?.toFloat() ?: throw GraphJsonException("$at.$name must be a number")

    private fun int(o: JsonObject, name: String, at: String): Int = float(o, name, at).toInt()

    private fun array(o: JsonObject, name: String, at: String): List<JsonValue> =
        (o[name] as? JsonArray)?.items ?: throw GraphJsonException("$at.$name must be an array")

    public companion object {
        /** The `format` field of every file. */
        public const val Format: String = "kompound-graph"

        /** The newest file version this code reads and writes. */
        public const val Version: Int = 1
    }
}

/** The JSON of the editor's graph and view. */
public fun KGraphState.toJson(json: GraphJson = GraphJson()): String = json.encode(graph, viewport)

/** Replaces the editor's graph (clearing history and selection) with [text]; the saved view is restored when the file has one. */
public fun KGraphState.loadJson(text: String, json: GraphJson = GraphJson()) {
    val loaded = json.decode(text)
    load(loaded.graph)
    if (loaded.offset != null && loaded.zoom != null) viewport.restore(loaded.offset, loaded.zoom)
}
