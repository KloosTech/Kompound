package tech.kloos.kompound.graph.runtime

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import tech.kloos.kompound.graph.model.SignalMode
import tech.kloos.kompound.graph.serialization.GraphJson
import tech.kloos.kompound.json.FieldInfo
import tech.kloos.kompound.json.JsonString
import tech.kloos.kompound.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SamplesTest {
    private val out = PortRef(NodeId("p"), PortId("out"))
    private val producerJson = """{"title":"T","description":"D","tasks":[{"name":"x"}]}"""

    private fun producer(type: PortType = PortType.json(), secret: Boolean = false, data: Any? = producerJson) =
        GraphNode(NodeId("p"), "producer", Offset.Zero, listOf(PortSpec.output("out", type = type, secret = secret)), data)

    private fun consumer(mode: SignalMode = SignalMode.Latest) =
        GraphNode(NodeId("c"), "consumer", Offset.Zero, listOf(PortSpec.input("in", signal = mode), PortSpec.output("out")))

    private val wire = Edge(EdgeId("w"), out, PortRef(NodeId("c"), PortId("in")))

    private fun TestScope.engine(runs: MutableList<String> = mutableListOf(), samples: SampleStore? = null, maxSampleBytes: Int = 64 * 1024) = GraphEngine(
        this,
        mapOf(
            "producer" to singleOutputRunner { n, _ -> n.data },
            "consumer" to singleOutputRunner { n, i -> runs += "consumer"; i["in"] },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
        samples = samples,
        maxSampleBytes = maxSampleBytes,
    )

    private fun graph(p: GraphNode = producer(), c: GraphNode = consumer()) = Graph.of(listOf(p, c), listOf(wire))

    private fun paths(f: List<FieldInfo>) = f.map { it.path }

    @Test
    fun theLastJsonValueOfAJsonPortIsItsSample() = runTest {
        val e = engine()
        e.update(graph())
        advanceUntilIdle()
        val sample = e.sampleOf(out)!!
        assertEquals("T", ((sample as tech.kloos.kompound.json.JsonObject)["title"] as JsonString).value, "text that parses as JSON is stored as JSON")
        assertEquals(listOf("title", "description", "tasks", "tasks[*].name"), paths(e.fieldsOf(NodeId("c"), "in")))
    }

    @Test
    fun portsThatAreNotJsonOrAreSecretKeepNoSample() = runTest {
        val plain = engine()
        plain.update(graph(producer(type = PortType.Any)))
        advanceUntilIdle()
        assertNull(plain.sampleOf(out))
        assertEquals(emptyList(), plain.fieldsOf(NodeId("c"), "in"))

        val secret = engine()
        secret.update(graph(producer(secret = true)))
        advanceUntilIdle()
        assertNull(secret.sampleOf(out), "secret values are never kept")
    }

    @Test
    fun bigSamplesAreCutDownToTheirShape() = runTest {
        val big = "[" + (1..2000).joinToString(",") { """{"id":$it,"name":"${"n".repeat(50)}"}""" } + "]"
        val e = engine(maxSampleBytes = 2_000)
        e.update(graph(producer(data = big)))
        advanceUntilIdle()
        val sample = e.sampleOf(out) as tech.kloos.kompound.json.JsonArray
        assertTrue(sample.items.size <= 3 && sample.toJson().length <= 2_000, "${sample.items.size} items, ${sample.toJson().length} bytes")
        assertEquals(listOf("[*].id", "[*].name"), paths(e.fieldsOf(NodeId("c"), "in")))
    }

    @Test
    fun aDeclaredSchemaGivesFieldsBeforeAnyRunAndTheSampleSuppliesExamples() = runTest {
        val schema = JsonValue.parse("""{"type":"object","properties":{"title":{"type":"string"},"description":{"type":"string"}}}""")
        val e = engine()
        e.update(graph(producer(type = PortType.json(schema))))
        // nothing has run yet: the engine only has the graph
        val before = e.fieldsOf(NodeId("c"), "in")
        assertEquals(listOf("title", "description"), paths(before))
        assertNull(before[0].example)
        advanceUntilIdle()
        val after = e.fieldsOf(NodeId("c"), "in")
        assertEquals(listOf("title", "description"), paths(after), "the schema decides which fields exist")
        assertEquals(JsonString("T"), after[0].example, "the real value is the example")
    }

    @Test
    fun aCollectInputSeesItsFieldsBehindStar() = runTest {
        val e = engine()
        e.update(graph(c = consumer(SignalMode.Collect)))
        advanceUntilIdle()
        assertEquals(listOf("[*].title", "[*].description", "[*].tasks", "[*].tasks[*].name"), paths(e.fieldsOf(NodeId("c"), "in")))
    }

    @Test
    fun noWireUnknownPortsAndOutputsGiveNoFields() = runTest {
        val e = engine()
        e.update(Graph.of(listOf(producer(), consumer()), emptyList()))
        advanceUntilIdle()
        assertEquals(emptyList(), e.fieldsOf(NodeId("c"), "in"), "not wired")
        e.update(graph())
        advanceUntilIdle()
        assertEquals(emptyList(), e.fieldsOf(NodeId("c"), "nope"))
        assertEquals(emptyList(), e.fieldsOf(NodeId("p"), "out"), "an output has no incoming fields")
        assertEquals(emptyList(), e.fieldsOf(NodeId("missing"), "in"))
    }

    @Test
    fun aSampleChangingDoesNotStaleOrRerunAnything() = runTest {
        val runs = mutableListOf<String>()
        val e = engine(runs)
        e.update(graph())
        advanceUntilIdle()
        assertEquals(1, runs.size)
        e.loadSamples(mapOf(out to JsonValue.parse("""{"other":1}""")))
        advanceUntilIdle()
        assertEquals(1, runs.size, "the consumer was not run again")
        assertTrue(e.runOf(NodeId("c")) is NodeRun.Done)
        assertEquals(listOf("other"), paths(e.fieldsOf(NodeId("c"), "in")))
    }

    @Test
    fun samplesOfRemovedOrRetypedPortsAreForgotten() = runTest {
        val e = engine()
        e.update(graph())
        advanceUntilIdle()
        assertTrue(e.sampleOf(out) != null)
        e.update(graph(producer(type = PortType.Any)))
        assertNull(e.sampleOf(out), "no longer a JSON port")
    }

    @Test
    fun aSampleStoreIsLoadedAtStartAndSavedAMomentAfterTheLastChange() = runTest {
        val store = InMemorySampleStore(mapOf(out to JsonValue.parse("""{"saved":true}""")))
        val e = engine(samples = store)
        // before any run, after "a restart": the fields come from the store
        e.update(Graph.of(listOf(producer(data = null), consumer()), listOf(wire)).let { it })
        assertEquals(listOf("saved"), paths(e.fieldsOf(NodeId("c"), "in")))
        e.update(graph())
        advanceTimeBy(100)
        assertEquals(setOf("saved"), (store.load()[out] as tech.kloos.kompound.json.JsonObject).fields.keys, "not saved yet: it waits for the changes to settle")
        advanceUntilIdle()
        assertTrue("title" in (store.load()[out] as tech.kloos.kompound.json.JsonObject).fields.keys)
        // flush saves at once
        e.loadSamples(mapOf(out to JsonValue.parse("""{"now":1}""")))
        e.flushSamples()
        assertEquals(setOf("now"), (store.load()[out] as tech.kloos.kompound.json.JsonObject).fields.keys)
    }

    @Test
    fun portSchemasAndOptInSamplesSurviveASaveAndLoad() = runTest {
        val schema = JsonValue.parse("""{"type":"object","properties":{"title":{"type":"string"}},"x-custom":{"keep":"me"}}""")
        val g = graph(producer(type = PortType.json(schema)))
        val json = GraphJson()
        val back = json.decode(json.encode(g)).graph.node(NodeId("p"))!!.port("out")!!
        assertEquals(PortType.json(schema), back.type, "the schema is saved with the port, unknown keywords included")
        assertEquals(PortType.json(), json.decode(json.encode(graph())).graph.node(NodeId("p"))!!.port("out")!!.type)

        val sample = mapOf(out to JsonValue.parse("""{"title":"T"}"""))
        assertTrue("samples" !in json.encode(g, samples = sample), "off by default")
        val withSamples = GraphJson(samples = true)
        val loaded = withSamples.decode(withSamples.encode(g, samples = sample))
        assertEquals(sample, loaded.samples)
        val e = engine()
        e.update(loaded.graph)
        e.loadSamples(loaded.samples)
        assertEquals(listOf("title"), paths(e.fieldsOf(NodeId("c"), "in")))
        assertFalse(json.decode(withSamples.encode(g, samples = sample)).samples.isNotEmpty(), "a GraphJson without samples = true ignores them on load")
    }

    @Test
    fun jsonPortsConnectToAnyAndToEachOther() {
        assertTrue(PortType.Any.accepts(PortType.json()))
        assertTrue(PortType.json().accepts(PortType.Any))
        assertTrue(PortType.json().accepts(PortType.json(JsonValue.parse("""{"type":"string"}"""))))
        assertFalse(PortType.json().accepts(PortType.of("number")))
    }
}
