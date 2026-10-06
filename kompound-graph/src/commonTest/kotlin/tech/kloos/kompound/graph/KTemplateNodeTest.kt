package tech.kloos.kompound.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import tech.kloos.kompound.graph.runtime.GraphEngine
import tech.kloos.kompound.graph.runtime.NodeRunner
import tech.kloos.kompound.graph.runtime.rememberGraphEngine
import tech.kloos.kompound.graph.runtime.render
import tech.kloos.kompound.graph.runtime.singleOutputRunner
import tech.kloos.kompound.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class KTemplateNodeTest {
    private val schema = JsonValue.parse("""{"type":"object","properties":{"title":{"type":"string"},"description":{"type":"string"}}}""")

    private fun producer(id: String = "p") =
        GraphNode(NodeId(id), "producer", Offset.Zero, listOf(PortSpec.output("out", type = PortType.json(schema))), """{"title":"T","description":"D"}""")

    private fun consumer(vararg inputs: String) = GraphNode(NodeId("c"), "consumer", Offset(300f, 0f), inputs.map { PortSpec.input(it) } + PortSpec.output("out"))

    private fun wire(from: String, port: String) = Edge(EdgeId("$from-$port"), PortRef(NodeId(from), PortId("out")), PortRef(NodeId("c"), PortId(port)))

    @Test
    fun oneInputGivesBarePathsAndSeveralGivePrefixedOnes() = runTest {
        val e = GraphEngine(this, mapOf("producer" to singleOutputRunner { n, _ -> n.data }), runDispatcher = StandardTestDispatcher(testScheduler))
        val single = consumer("in")
        e.update(Graph.of(listOf(producer(), single), listOf(wire("p", "in"))))
        val one = e.templateFields(single)
        assertEquals(listOf("title", "description"), one.fields.map { it.path })
        assertEquals(true, one.isKnown("in.title"), "the prefixed form is accepted too")
        assertEquals(true, one.isKnown("in"), "the whole input")
        assertEquals(false, one.isKnown("in.nope"))

        val double = consumer("a", "b")
        e.update(Graph.of(listOf(producer("p"), producer("q"), double), listOf(wire("p", "a"), wire("q", "b"))))
        val two = e.templateFields(double)
        assertEquals(listOf("a.title", "a.description", "b.title", "b.description"), two.fields.map { it.path })
        assertEquals(listOf("b.title", "b.description"), e.templateFields(double, from = "b").fields.map { it.path })
        advanceUntilIdle()
    }

    @Test
    fun typingBracesInANodeFieldOffersTheProducersFieldsAndTheRenderedTextMatches() = runComposeUiTest {
        var body by mutableStateOf("")
        var engine: GraphEngine? = null
        val graph = Graph.of(listOf(producer(), consumer("in")), listOf(wire("p", "in")))
        setContent {
            MaterialTheme(lightColorScheme()) {
                val state = remember { KGraphState(graph) }
                val e = rememberGraphEngine(
                    state,
                    mapOf(
                        "producer" to singleOutputRunner { n, _ -> n.data },
                        "consumer" to NodeRunner { ctx -> mapOf("out" to ctx.render(body)) },
                    ),
                )
                engine = e
                Box(Modifier.size(900.dp, 600.dp)) {
                    KNodeGraph(state, Modifier.fillMaxSize()) { node ->
                        if (node.kind == "consumer") {
                            KNode(node, "Consumer") {
                                Input("in")
                                Content { TemplateField(e, body, { body = it }, from = "in", label = "Body") }
                            }
                        } else KNode(node, "Producer") { Output("out") }
                    }
                }
            }
        }
        waitForIdle()
        // the fields are known before anything asked for them: from the schema of the producer's port, with the run's values as examples
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("{\"a\": \"{{")
        waitForIdle()
        onNodeWithText("description").assertIsDisplayed()
        onNodeWithText("description").performClick()
        waitForIdle()
        assertEquals("{\"a\": \"{{description}}", body.take("{\"a\": \"{{description}}".length))
        // a misspelling is reported before the run
        body = "{{tilte}}"
        waitForIdle()
        onNodeWithText("Unknown field \"tilte\"").assertIsDisplayed()
    }
}
