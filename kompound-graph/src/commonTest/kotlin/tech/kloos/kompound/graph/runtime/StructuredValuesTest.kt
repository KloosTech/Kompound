package tech.kloos.kompound.graph.runtime

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.graph.serialization.GraphJson
import tech.kloos.kompound.json.FieldInfo
import tech.kloos.kompound.json.JsonObject
import tech.kloos.kompound.json.JsonValue
import tech.kloos.kompound.json.MissingFieldException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The acceptance test of ADR 0007 (parts 1 to 3 and 5; the editor part is KTemplateNodeTest) plus slice 4's schemaFor, item-of ports and wire warnings. */
@OptIn(ExperimentalCoroutinesApi::class)
class StructuredValuesTest {
    private val schema = """{"type":"object","properties":{"title":{"type":"string"},"description":{"type":"string"}}}"""
    private val output = """{"title":"T","description":"D"}"""
    private val out = PortRef(NodeId("p"), PortId("out"))

    /** Producer: its data is the schema (as text) of what it produces; schemaFor reads it, so editing it changes the shape. */
    private fun producer(data: String? = schema, type: PortType = PortType.json()) =
        GraphNode(NodeId("p"), "producer", Offset.Zero, listOf(PortSpec.output("out", type = type)), data)

    private fun consumer(body: String = "") = GraphNode(NodeId("c"), "consumer", Offset(300f, 0f), listOf(PortSpec.input("in", type = PortType.json()), PortSpec.output("out")), body)

    private val wire = Edge(EdgeId("w"), out, PortRef(NodeId("c"), PortId("in")))

    private val schemaFor: (GraphNode) -> Map<PortId, JsonValue> = { node ->
        if (node.kind == "producer" && node.data is String) mapOf(PortId("out") to JsonValue.parse(node.data as String)) else emptyMap()
    }

    private fun TestScope.engine(samples: SampleStore? = null, withSchema: Boolean = true) = GraphEngine(
        this,
        mapOf(
            "producer" to singleOutputRunner { _, _ -> output },
            "consumer" to NodeRunner { ctx -> mapOf("out" to ctx.render(ctx.node.data as String)) },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
        samples = samples,
        schemaFor = if (withSchema) schemaFor else null,
    )

    private fun paths(f: List<FieldInfo>) = f.map { it.path }

    @Test
    fun acceptance_fieldsBeforeAnyRunFromSchemaForThenFromTheSampleAloneThenAfterARestart() = runTest {
        // 2a: before any run the declared shape gives the fields
        val e = engine()
        e.update(Graph.of(listOf(producer(), consumer()), listOf(wire)))
        assertEquals(listOf("title", "description"), paths(e.fieldsOf(NodeId("c"), "in")))
        advanceUntilIdle()
        assertEquals(JsonValue.parse(output), e.sampleOf(out))

        // 2b: with the schema removed the sample alone gives them
        e.update(Graph.of(listOf(producer(data = null), consumer()), listOf(wire)))
        assertEquals(listOf("title", "description"), paths(e.fieldsOf(NodeId("c"), "in")))
    }

    @Test
    fun acceptance_afterARestartWithASampleStoreOrSamplesInTheFile() = runTest {
        val store = InMemorySampleStore()
        val first = engine(store)
        first.update(Graph.of(listOf(producer(data = null), consumer()), listOf(wire)))
        advanceUntilIdle()
        first.flushSamples()
        // a new engine (the "restart") that has not run anything knows the fields
        val second = engine(store, withSchema = false)
        second.update(Graph.of(listOf(producer(data = null), consumer()), listOf(wire)))
        assertEquals(listOf("title", "description"), paths(second.fieldsOf(NodeId("c"), "in")))

        // the same through the graph file
        val json = GraphJson(samples = true)
        val text = json.encode(Graph.of(listOf(producer(data = null), consumer()), listOf(wire)), samples = first.sampleSnapshot())
        val loaded = json.decode(text)
        val third = engine(withSchema = false)
        third.update(loaded.graph)
        third.loadSamples(loaded.samples)
        assertEquals(listOf("title", "description"), paths(third.fieldsOf(NodeId("c"), "in")))
    }

    @Test
    fun acceptance_renderAndTheNamedFailure() = runTest {
        val good = engine()
        good.update(Graph.of(listOf(producer(), consumer("""{"a": "{{title}}", "b": "{{description}}"}""")), listOf(wire)))
        advanceUntilIdle()
        assertEquals("""{"a": "T", "b": "D"}""", good.output(NodeId("c"), "out"))

        val bad = engine()
        bad.update(Graph.of(listOf(producer(), consumer("{{tilte}}")), listOf(wire)))
        advanceUntilIdle()
        val failure = (bad.runOf(NodeId("c")) as NodeRun.Failed).error as MissingFieldException
        assertEquals(Triple("c", "in", "tilte"), Triple(failure.node, failure.port, failure.path))
    }

    @Test
    fun acceptance_aSampleChangingDoesNotMakeTheConsumerStale() = runTest {
        var runs = 0
        val e = GraphEngine(
            this,
            mapOf("producer" to singleOutputRunner { _, _ -> output }, "consumer" to singleOutputRunner { _, _ -> runs++ }),
            runDispatcher = StandardTestDispatcher(testScheduler),
            schemaFor = schemaFor,
        )
        e.update(Graph.of(listOf(producer(), consumer()), listOf(wire)))
        advanceUntilIdle()
        e.loadSamples(mapOf(out to JsonValue.parse("""{"title":"changed"}""")))
        advanceUntilIdle()
        assertEquals(1, runs)
        assertTrue(e.runOf(NodeId("c")) is NodeRun.Done)
    }

    @Test
    fun schemaForFollowsEditsOfTheDataAndWinsOverThePortSchema() = runTest {
        val e = engine()
        val typed = PortType.json(JsonValue.parse("""{"type":"object","properties":{"fromType":{"type":"string"}}}"""))
        e.update(Graph.of(listOf(producer(type = typed), consumer()), listOf(wire)))
        assertEquals(listOf("title", "description"), paths(e.fieldsOf(NodeId("c"), "in")), "schemaFor beats the schema of the port type")
        e.update(Graph.of(listOf(producer(data = """{"type":"object","properties":{"other":{"type":"integer"}}}""", type = typed), consumer()), listOf(wire)))
        assertEquals(listOf("other"), paths(e.fieldsOf(NodeId("c"), "in")), "editing the data changes the shape")
        e.update(Graph.of(listOf(producer(data = null, type = typed), consumer()), listOf(wire)))
        assertEquals(listOf("fromType"), paths(e.fieldsOf(NodeId("c"), "in")), "without schemaFor's answer the port type's schema is used")
        assertNull(e.schemaOf(PortRef(NodeId("c"), PortId("out"))))
    }

    @Test
    fun anItemOfPortKeepsTheFieldsFlowingThroughALoop() = runTest {
        val listSchema = """{"type":"array","items":{"type":"object","properties":{"id":{"type":"integer"},"name":{"type":"string"}}}}"""
        val each = GraphNode(NodeId("e"), "each", Offset(150f, 0f), listOf(PortSpec.input("list", type = PortType.json()), PortSpec.output("item", type = PortType.itemOf("list"))))
        val after = GraphNode(NodeId("a"), "after", Offset(300f, 0f), listOf(PortSpec.input("in", type = PortType.json())))
        val g = Graph.of(
            listOf(producer(data = listSchema), each, after),
            listOf(Edge(EdgeId("1"), out, PortRef(NodeId("e"), PortId("list"))), Edge(EdgeId("2"), PortRef(NodeId("e"), PortId("item")), PortRef(NodeId("a"), PortId("in")))),
        )
        val e = engine()
        e.update(g)
        assertEquals(listOf("id", "name"), paths(e.fieldsOf(NodeId("a"), "in")), "the loop's output has the item shape of its input array")
        assertEquals(JsonValue.parse("""{"type":"object","properties":{"id":{"type":"integer"},"name":{"type":"string"}}}"""), e.schemaOf(PortRef(NodeId("e"), PortId("item"))))
        // a changed upstream shape flows through
        e.update(g.withNode(g.node(NodeId("p"))!!.copy(data = """{"type":"array","items":{"type":"object","properties":{"only":{"type":"string"}}}}""")))
        assertEquals(listOf("only"), paths(e.fieldsOf(NodeId("a"), "in")))
        // without a schema the first item of the upstream sample is used
        val plain = engine(withSchema = false)
        plain.update(g.withNode(g.node(NodeId("p"))!!.copy(data = null)))
        plain.loadSamples(mapOf(out to JsonValue.parse("""[{"x":1},{"x":2}]""")))
        assertEquals(listOf("x"), paths(plain.fieldsOf(NodeId("a"), "in")))
    }

    @Test
    fun arrayOfDescribesTheCollectingSide() {
        val type = PortType.arrayOf(JsonValue.parse("""{"type":"object","properties":{"id":{"type":"integer"}}}"""))
        val fields = tech.kloos.kompound.json.JsonFields.ofSchema((type as tech.kloos.kompound.graph.model.JsonPortType).schema!!)
        assertEquals(listOf("[*].id"), fields.map { it.path })
        assertEquals(PortType.arrayOf(), PortType.json(JsonValue.parse("""{"type":"array"}""")), "no item shape is a plain array")
    }

    @Test
    fun aWireWhoseShapeCannotFitGetsAWarningButIsNeverBlocked() = runTest {
        val wanting = GraphNode(
            NodeId("c"), "consumer", Offset(300f, 0f),
            listOf(PortSpec.input("in", type = PortType.json(JsonValue.parse("""{"type":"object","required":["title","author"],"properties":{"title":{"type":"string"},"author":{"type":"string"}}}""")))),
            "",
        )
        val e = engine()
        e.update(Graph.of(listOf(producer(), wanting), listOf(wire)))
        assertEquals("missing field \"author\"", e.wireWarning(wire))
        val satisfied = wanting.copy(ports = listOf(PortSpec.input("in", type = PortType.json(JsonValue.parse("""{"type":"object","required":["title"],"properties":{"title":{"type":"string"}}}""")))))
        e.update(Graph.of(listOf(producer(), satisfied), listOf(wire)))
        assertNull(e.wireWarning(wire))
        // either side without a shape: no opinion
        e.update(Graph.of(listOf(producer(data = null), wanting), listOf(wire)))
        assertNull(e.wireWarning(wire))
    }

    @Test
    fun itemOfSurvivesASaveAndLoad() {
        val node = GraphNode(NodeId("e"), "each", Offset.Zero, listOf(PortSpec.input("list", type = PortType.json()), PortSpec.output("item", type = PortType.itemOf("list"))))
        val back = GraphJson().decode(GraphJson().encode(Graph.of(listOf(node), emptyList()))).graph.node(NodeId("e"))!!
        assertEquals(PortType.itemOf("list"), back.port("item")!!.type)
        assertEquals(PortType.json(), back.port("list")!!.type)
        assertTrue("itemOf" !in GraphJson(pretty = false).encode(Graph.of(listOf(producer()), emptyList())))
    }
}
