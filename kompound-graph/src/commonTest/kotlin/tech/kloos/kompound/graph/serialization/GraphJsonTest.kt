package tech.kloos.kompound.graph.serialization

import androidx.compose.ui.geometry.Offset
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.GroupId
import tech.kloos.kompound.graph.model.NodeGroup
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.graph.model.SignalMode
import tech.kloos.kompound.graph.model.math
import tech.kloos.kompound.graph.model.applyTo
import tech.kloos.kompound.graph.model.ref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonTest {
    @Test
    fun parsesAndWritesEveryValueKind() {
        val text = """{"a":[1,-2.5,1e3,true,false,null,"x\n\"\u00e9\\"],"b":{},"c":[]}"""
        val value = JsonValue.parse(text)
        assertEquals(value, JsonValue.parse(value.toJson()))
        assertEquals(value, JsonValue.parse(value.toJson(pretty = true)))
        val a = ((value as JsonObject)["a"] as JsonArray).items
        assertEquals(JsonNumber(1000.0), a[2])
        assertEquals(JsonString("x\n\"é\\"), a[6])
    }

    @Test
    fun rejectsBrokenText() {
        for (bad in listOf("", "{", "[1,]", "{\"a\" 1}", "tru", "\"abc", "[1] x", "{\"a\":01x}", "\"\\q\"")) {
            assertFailsWith<GraphJsonException>(bad) { JsonValue.parse(bad) }
        }
        assertFailsWith<GraphJsonException> { JsonValue.parse("[".repeat(2000)) }
    }

    @Test
    fun writesWholeNumbersWithoutADecimalPoint() {
        assertEquals("[1,2.5,-3]", JsonArray(listOf(JsonNumber(1.0), JsonNumber(2.5), JsonNumber(-3.0))).toJson())
    }
}

class GraphJsonTest {
    private data class Script(val command: String, val timeout: Int)

    private val script = nodeDataCodec<Script>(
        encode = { jsonObjectOf("command" to JsonString(it.command), "timeout" to JsonNumber(it.timeout.toDouble())) },
        decode = { v -> (v as JsonObject).let { Script((it["command"] as JsonString).value, (it["timeout"] as JsonNumber).value.toInt()) } },
    )

    private fun sample(): Graph = Graph.of(
        listOf(
            math("a", Offset(10.5f, -20.25f)).copy(data = 3.7f, group = GroupId("g")),
            math("b", Offset(300f, 0f)).copy(data = 4, group = GroupId("g")),
            math("c", Offset(600f, 80f)).copy(data = "text"),
            math("d", Offset(900f, 80f)).copy(data = true),
            GraphNode(NodeId("s"), "script", Offset(0f, 300f), listOf(PortSpec.output("stdout", "Output", PortType.of("text"))), Script("echo hi \"there\"", 30)),
            GraphNode(NodeId("inner"), "math", Offset(5f, 5f), emptyList(), 12L, scope = NodeId("c")),
            GraphNode(NodeId("u"), "unknown.kind", Offset.Zero, emptyList(), JsonObject(mapOf("k" to JsonArray(listOf(JsonNumber(1.0), JsonNull))))),
        ),
        listOf(
            Edge(EdgeId("e1"), ref("a", "out"), ref("c", "a")),
            Edge(EdgeId("e2"), ref("b", "out"), ref("c", "b")),
        ),
        listOf(NodeGroup(GroupId("g"), "Inputs \u00e9", collapsed = true, color = 3)),
    )

    private val json = GraphJson(mapOf("script" to script), listOf(PortType.of("text")))

    @Test
    fun aGraphSurvivesAnEncodeAndDecode() {
        val graph = sample()
        val back = json.decode(json.encode(graph)).graph
        assertEquals(graph.nodes, back.nodes)
        assertEquals(graph.edges, back.edges)
        assertEquals(graph.groups, back.groups)
        assertEquals(listOf("a", "b", "c", "d", "s", "inner", "u"), back.nodes.keys.map { it.value }, "node order is kept")
        assertEquals(3.7f, back.node(NodeId("a"))!!.data)
        assertEquals(Script("echo hi \"there\"", 30), back.node(NodeId("s"))!!.data)
        assertEquals(NodeId("c"), back.node(NodeId("inner"))!!.scope)
    }

    @Test
    fun compactAndPrettyOutputsDecodeTheSame() {
        val graph = sample()
        val a = GraphJson(mapOf("script" to script), pretty = false).encode(graph)
        val b = GraphJson(mapOf("script" to script), pretty = true).encode(graph)
        assertTrue(a.length < b.length)
        assertEquals(json.decode(a).graph.nodes, json.decode(b).graph.nodes)
    }

    @Test
    fun kindsWithoutACodecKeepTheirDataAsRawJsonSoSavingAgainLosesNothing() {
        val text = json.encode(sample())
        val withoutCodec = GraphJson()
        val loaded = withoutCodec.decode(text).graph
        val raw = loaded.node(NodeId("s"))!!.data
        assertTrue(raw is JsonObject, "$raw")
        assertEquals(json.decode(withoutCodec.encode(loaded)).graph.node(NodeId("s"))!!.data, Script("echo hi \"there\"", 30))
    }

    @Test
    fun dataOfAnUnregisteredTypeFailsWithAClearMessage() {
        val graph = Graph.of(listOf(math("a", Offset.Zero).copy(kind = "custom", data = Script("x", 1))))
        val e = assertFailsWith<GraphJsonException> { GraphJson().encode(graph) }
        assertTrue("custom" in e.message!! && "NodeDataCodec" in e.message!!, e.message)
    }

    @Test
    fun portTypesComeBackAsTheAppsOwnObjects() {
        val custom = object : PortType {
            override val id = "text"
            override fun accepts(source: PortType) = source.id == "text" || source.id == "number"
        }
        val g = GraphJson(portTypes = listOf(custom))
        val loaded = g.decode(g.encode(Graph.of(listOf(GraphNode(NodeId("n"), "k", ports = listOf(PortSpec.input("p", type = custom))))))).graph
        assertTrue(loaded.node(NodeId("n"))!!.port("p")!!.type === custom)
    }

    @Test
    fun theViewportIsSavedAndRestoredByTheStateHelpers() {
        val state = KGraphState(sample().let { it.withoutGroup(GroupId("g")) })
        state.viewport.restore(Offset(120f, -40f), 1.5f)
        val text = state.toJson(json)
        val other = KGraphState()
        other.loadJson(text, json)
        assertEquals(Offset(120f, -40f), other.viewport.offset)
        assertEquals(1.5f, other.viewport.zoom)
        assertEquals(state.graph.nodes.size, other.graph.nodes.size)
        assertTrue(!other.canUndo)
    }

    @Test
    fun brokenOrInconsistentFilesAreRejectedWithAReason() {
        fun fails(text: String, part: String) {
            val e = assertFailsWith<GraphJsonException>(text) { GraphJson().decode(text) }
            assertTrue(part in e.message!!, "${e.message} should mention $part")
        }
        fails("[]", "object")
        fails("""{"format":"other","version":1,"nodes":[],"edges":[]}""", "Kompound graph")
        fails("""{"format":"kompound-graph","version":99,"nodes":[],"edges":[]}""", "version")
        fails("""{"format":"kompound-graph","version":1,"edges":[]}""", "nodes")
        val node = """{"id":"a","kind":"k","x":0,"y":0,"ports":[{"id":"out","direction":"Output"}]}"""
        fails("""{"format":"kompound-graph","version":1,"nodes":[$node,$node],"edges":[]}""", "Duplicate node")
        fails("""{"format":"kompound-graph","version":1,"nodes":[$node],"edges":[{"id":"e","from":{"node":"a","port":"out"},"to":{"node":"z","port":"in"}}]}""", "missing port")
        fails("""{"format":"kompound-graph","version":1,"nodes":[{"id":"a","kind":"k","x":0,"y":0,"ports":[],"group":"nope"}],"edges":[]}""", "missing group")
        fails("""{"format":"kompound-graph","version":1,"nodes":[{"id":"a","kind":"k","x":0,"y":0,"ports":[{"id":"p","direction":"Sideways"}]}],"edges":[]}""", "direction")
    }

    @Test
    fun unknownFieldsAreIgnoredSoNewerFilesWithinTheSameVersionStillLoad() {
        val text = """{"format":"kompound-graph","version":1,"future":{"x":1},"nodes":[{"id":"a","kind":"k","x":1,"y":2,"ports":[],"extra":true}],"edges":[]}"""
        val loaded = GraphJson().decode(text)
        assertEquals(Offset(1f, 2f), loaded.graph.node(NodeId("a"))!!.position)
        assertEquals(null, loaded.zoom)
    }

    @Test
    fun signalModesOfPortsRoundTripAndTheDefaultIsLeftOut() {
        val ports = SignalMode.entries.map { PortSpec.input(it.name, signal = it) }
        val graph = Graph.of(listOf(GraphNode(NodeId("n"), "k", ports = ports)))
        val text = GraphJson(pretty = false).encode(graph)
        assertTrue("\"Each\"" in text && text.split("\"signal\"").size - 1 == SignalMode.entries.size - 1, "Latest is not written")
        assertEquals(ports, GraphJson().decode(text).graph.node(NodeId("n"))!!.ports)
    }

    @Test
    fun subgraphsWithBoundaryNodesRoundTrip() {
        val base = Graph.of(
            listOf(math("a", Offset(0f, 0f)), math("b", Offset(300f, 0f)), math("c", Offset(600f, 0f))),
            listOf(Edge(EdgeId("e1"), ref("a", "out"), ref("b", "a")), Edge(EdgeId("e2"), ref("b", "out"), ref("c", "a"))),
        )
        val command = tech.kloos.kompound.graph.model.Subgraphs.create(base, setOf(NodeId("b")), NodeId("sub"))!!
        val graph = command.applyTo(base)!!.graph
        assertTrue(graph.nodes.values.any { it.kind == "subgraph.input" })
        val back = GraphJson().decode(GraphJson().encode(graph)).graph
        assertEquals(graph.nodes, back.nodes)
        assertEquals(graph.edges, back.edges)
    }
}

class ValueJsonTest {
    private data class Money(val cents: Long)

    private val json = ValueJson(listOf(valueCodec<Money>("money", { JsonNumber(it.cents.toDouble()) }, { Money((it as JsonNumber).value.toLong()) })))

    @Test
    fun everySupportedTypeComesBackWithItsType() {
        val values: List<Any?> = listOf(
            null, true, "x", 1.5, 7, 9_000_000_000_000L, 2.5f, JsonObject(mapOf("k" to JsonNumber(1.0))),
            listOf(1, "two", listOf(3.0)), mapOf("a" to 1, "b" to mapOf("c" to 2f)), mapOf("\$int" to "tricky", "x" to 1), Money(250),
        )
        for (v in values) {
            val back = json.decode(JsonValue.parse(json.encode(v).toJson()))
            assertEquals(v, back, "$v")
            if (v !is List<*> && v !is Map<*, *>) assertEquals(v?.let { it::class }, back?.let { it::class }, "$v keeps its type")
        }
    }

    @Test
    fun plainValuesAreWrittenAsPlainJson() {
        assertEquals("""{"a":[1.5,true,"x",null]}""", json.encode(mapOf("a" to listOf(1.5, true, "x", null))).toJson())
    }

    @Test
    fun unknownTypesAndBadTagsFailClearly() {
        class Custom
        val e = assertFailsWith<GraphJsonException> { json.encode(Custom()) }
        assertTrue("Custom" in e.message!!)
        assertFailsWith<GraphJsonException> { json.encode(mapOf(1 to 2)) }
        assertFailsWith<GraphJsonException> { json.decode(JsonValue.parse("""{"${'$'}type":"nope","value":1}""")) }
        assertFailsWith<GraphJsonException> { json.decode(JsonValue.parse("""{"${'$'}int":"x"}""")) }
    }

    @Test
    fun pinsAreSavedInTheGraphFileAndLoadedBack() {
        val pin = mapOf(tech.kloos.kompound.graph.model.PortId("out") to (listOf(1, 2) as Any?), tech.kloos.kompound.graph.model.PortId("n") to 3f)
        val graph = Graph.of(listOf(GraphNode(NodeId("a"), "k", ports = listOf(PortSpec.output("out"), PortSpec.output("n")), pin = pin)))
        val g = GraphJson(values = json)
        val back = g.decode(g.encode(graph)).graph
        assertEquals(pin, back.node(NodeId("a"))!!.pin)
        assertEquals(null, GraphJson().decode(GraphJson().encode(Graph.of(listOf(GraphNode(NodeId("b"), "k"))))).graph.node(NodeId("b"))!!.pin)
    }

    @Test
    fun aMigrationHookFixesUpNodesSavedByAnOlderVersionWhileTheyLoad() {
        val old = Graph.of(listOf(GraphNode(NodeId("t"), "transform", ports = listOf(PortSpec.input("a"), PortSpec.output("out")))))
        val text = GraphJson().encode(old)
        val migrated = GraphJson(migrate = { n ->
            if (n.kind == "transform") n.copy(ports = n.ports.map { if (it.id.value == "a") it.copy(signal = tech.kloos.kompound.graph.model.SignalMode.Each) else it }) else n
        }).decode(text).graph
        assertEquals(tech.kloos.kompound.graph.model.SignalMode.Each, migrated.node(NodeId("t"))!!.port("a")!!.signal)
        assertEquals(tech.kloos.kompound.graph.model.SignalMode.Latest, GraphJson().decode(text).graph.node(NodeId("t"))!!.port("a")!!.signal)
    }

    @Test
    fun renamingAKindThatHasTypedDataReadsTheDataWithTheNewKindsCodec() {
        data class Script(val command: String, val timeout: Int)
        val script = nodeDataCodec<Script>(
            encode = { jsonObjectOf("command" to JsonString(it.command), "timeout" to JsonNumber(it.timeout.toDouble())) },
            decode = { v -> (v as JsonObject).let { Script((it["command"] as JsonString).value, (it["timeout"] as JsonNumber).value.toInt()) } },
        )
        val old = GraphJson(mapOf("script.v1" to script))
        val text = old.encode(Graph.of(listOf(GraphNode(NodeId("s"), "script.v1", data = Script("ls -la", 5)))))
        val renamed = GraphJson(
            mapOf("script" to script),
            migrate = { n -> if (n.kind == "script.v1") n.copy(kind = "script") else n },
        ).decode(text).graph.node(NodeId("s"))!!
        assertEquals("script", renamed.kind)
        assertEquals(Script("ls -la", 5), renamed.data, "typed, not raw JSON")
        // when migrate changes the data itself, that wins
        val changed = GraphJson(
            mapOf("script" to script),
            migrate = { n -> if (n.kind == "script.v1") n.copy(kind = "script", data = Script("custom", 1)) else n },
        ).decode(text).graph.node(NodeId("s"))!!
        assertEquals(Script("custom", 1), changed.data)
    }
}
