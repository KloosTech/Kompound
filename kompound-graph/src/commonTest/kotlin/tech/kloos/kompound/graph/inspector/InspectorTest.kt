package tech.kloos.kompound.graph.inspector

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.KNode
import tech.kloos.kompound.graph.KNodeGraph
import tech.kloos.kompound.graph.KNodeStatus
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.runtime.GraphEngine
import tech.kloos.kompound.graph.runtime.NodeRunner
import tech.kloos.kompound.graph.runtime.rememberGraphEngine
import tech.kloos.kompound.graph.runtime.singleOutputRunner
import tech.kloos.kompound.graph.serialization.JsonArray
import tech.kloos.kompound.graph.serialization.JsonNumber
import tech.kloos.kompound.graph.serialization.JsonObject
import tech.kloos.kompound.graph.serialization.JsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValueViewTest {
    @Test
    fun displayValueMakesPlainJsonOutOfAnything() {
        val tree = displayValue(mapOf("n" to 1, "f" to 2.5f, "l" to listOf("a", null), "o" to object { override fun toString() = "custom" }))
        assertEquals(
            """{"n":1,"f":2.5,"l":["a",null],"o":"custom"}""",
            tree.toJson(),
        )
    }

    @Test
    fun schemaListsEveryFieldWithItsTypeAndShowsTheShapeOfArrays() {
        val rows = schemaRows(displayValue(mapOf("name" to "x", "items" to listOf(mapOf("n" to 1), mapOf("n" to 2)), "ok" to true)), 100)
        assertEquals(
            listOf("value object(3)", "name string", "items array(2)", "[0] object(1)", "n number", "… 1 more ", "ok boolean"),
            rows.map { "${it.name} ${it.type}".trimEnd().let { s -> if (it.type.isEmpty()) "$s " else s } },
        )
        assertEquals("\"x\"", rows[1].preview)
        assertEquals(listOf(0, 1, 1, 2, 3, 2, 1), rows.map { it.depth }, "indentation follows the nesting")
    }

    @Test
    fun aListOfObjectsBecomesATableWithTheUnionOfTheirKeys() {
        val (columns, rows) = tableOf(displayValue(listOf(mapOf("a" to 1, "b" to "x"), mapOf("a" to 2, "c" to true))), 100)
        assertEquals(listOf("a", "b", "c"), columns)
        assertEquals(listOf(listOf("1", "x", ""), listOf("2", "", "true")), rows)
        assertEquals(listOf("key", "value") to listOf(listOf("k", "v")), tableOf(JsonObject(mapOf("k" to JsonString("v"))), 10))
        assertEquals(listOf("index", "value") to listOf(listOf("0", "5")), tableOf(JsonArray(listOf(JsonNumber(5.0))), 10))
        assertEquals(listOf("value") to listOf(listOf("hi")), tableOf(JsonString("hi"), 10))
    }

    @Test
    fun previewsAreShortAndReadable() {
        assertEquals("\"hi\"", previewValue("hi"))
        assertEquals("\"a very long s…\"", previewValue("a very long string indeed"))
        assertEquals("3", previewValue(3))
        assertEquals("2.5", previewValue(2.5f))
        assertEquals("1 item", previewValue(listOf(1)))
        assertEquals("4 items", previewValue(listOf(1, 2, 3, 4)))
        assertEquals("2 fields", previewValue(mapOf("a" to 1, "b" to 2)))
        assertEquals("null", previewValue(null))
    }
}

@OptIn(ExperimentalTestApi::class)
class InspectorUiTest {
    private val scheme = lightColorScheme()

    private val graph = Graph.of(
        listOf(
            GraphNode(NodeId("a"), "const", Offset.Zero, listOf(PortSpec.output("out")), 4),
            GraphNode(NodeId("b"), "const", Offset(300f, 0f), listOf(PortSpec.output("out")), 5),
            GraphNode(NodeId("sum"), "work", Offset(600f, 0f), listOf(PortSpec.input("a"), PortSpec.input("b"), PortSpec.output("out"))),
        ),
        listOf(
            Edge(EdgeId("1"), PortRef(NodeId("a"), PortId("out")), PortRef(NodeId("sum"), PortId("a"))),
            Edge(EdgeId("2"), PortRef(NodeId("b"), PortId("out")), PortRef(NodeId("sum"), PortId("b"))),
        ),
    )

    private val runners = mapOf(
        "const" to singleOutputRunner { n, _ -> n.data },
        "work" to NodeRunner { ctx ->
            ctx.log("adding")
            delay(20)
            mapOf("out" to (ctx.inputs["a"] as Number).toDouble() + (ctx.inputs["b"] as Number).toDouble())
        },
    )

    @Test
    fun theInspectorShowsWhatCameInAndWhatWentOut() = runComposeUiTest {
        var engine: GraphEngine? = null
        setContent {
            MaterialTheme(scheme) {
                val state = remember { KGraphState(graph) }
                engine = rememberGraphEngine(state, runners)
                Box(Modifier.size(1100.dp, 600.dp)) { KNodeInspector(engine!!, state.graph.node(NodeId("sum"))!!, state = state, parameters = { androidx.compose.material3.Text("my settings") }) }
            }
        }
        waitUntil(timeoutMillis = 5_000) { engine?.runOf(NodeId("sum")) is tech.kloos.kompound.graph.runtime.NodeRun.Done }
        waitForIdle()
        onNodeWithText("work · sum").assertIsDisplayed()
        onNodeWithText("Succeeded").assertIsDisplayed()
        onNodeWithText("my settings").assertIsDisplayed()
        // input JSON shows both values, output JSON shows the sum
        onNodeWithText("\"a\": 4,", substring = true).assertIsDisplayed()
        onNodeWithText("9", substring = true).assertExists()
    }

    @Test
    fun testStepRunsTheNodeAloneWithEditedInputAndPinOutputPinsIt() = runComposeUiTest {
        var engine: GraphEngine? = null
        var state: KGraphState? = null
        setContent {
            MaterialTheme(scheme) {
                val s = remember { KGraphState(graph) }
                state = s
                engine = rememberGraphEngine(s, runners)
                Box(Modifier.size(1100.dp, 600.dp)) { KNodeInspector(engine!!, s.graph.node(NodeId("sum"))!!, state = s) }
            }
        }
        waitUntil(timeoutMillis = 5_000) { engine?.runOf(NodeId("sum")) is tech.kloos.kompound.graph.runtime.NodeRun.Done }
        waitForIdle()
        val executions = engine!!.executions.size
        onNodeWithText("Edit input").performClick()
        waitForIdle()
        onNodeWithText("Test step").performClick()
        waitUntil(timeoutMillis = 5_000) { engine!!.executions.size > executions && engine!!.executions.last().status != tech.kloos.kompound.graph.runtime.TraceStatus.Running }
        assertEquals(tech.kloos.kompound.graph.runtime.TraceTrigger.Test, engine!!.executions.last().trigger)
        assertEquals(9.0, engine!!.output(NodeId("sum"), "out"), "the graph's own result is untouched")
        onNodeWithText("Pin output").performClick()
        waitForIdle()
        assertEquals(mapOf(PortId("out") to 9.0), state!!.graph.node(NodeId("sum"))!!.pin)
        onNodeWithText("Unpin").assertIsDisplayed()
    }

    @Test
    fun nodesShowAStatusMarkAndWiresShowTheValueThatPassed() = runComposeUiTest {
        var engine: GraphEngine? = null
        setContent {
            MaterialTheme(scheme) {
                val state = remember { KGraphState(graph) }
                engine = rememberGraphEngine(state, runners)
                Box(Modifier.size(1000.dp, 500.dp)) {
                    KNodeGraph(
                        state, Modifier, fitOnFirstLayout = true,
                        nodeStatus = { engine!!.nodeStatus(it) },
                        edgeLabel = { engine!!.edgeLabel(it) },
                    ) { node -> KNode(node, node.id.value) { if (node.kind == "work") { Input("a"); Input("b") }; Output("out") } }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { engine?.runOf(NodeId("sum")) is tech.kloos.kompound.graph.runtime.NodeRun.Done }
        waitForIdle()
        val marks = onAllNodesWithContentDescription("Done", useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(3, marks, "status marks")
        assertEquals(KNodeStatus.Done, engine!!.nodeStatus(graph.node(NodeId("sum"))!!))
        assertEquals("4", engine!!.edgeLabel(graph.edges.getValue(EdgeId("1"))))
        val execution = engine!!.executions.single()
        assertEquals(KNodeStatus.Done, engine!!.nodeStatus(graph.node(NodeId("a"))!!, execution))
        assertEquals("5", engine!!.edgeLabel(graph.edges.getValue(EdgeId("2")), execution))
    }

    @Test
    fun theExecutionListShowsRunsNewestFirstAndReportsTheSelection() = runComposeUiTest {
        var engine: GraphEngine? = null
        var picked: tech.kloos.kompound.graph.runtime.Execution? = null
        var pickedSomething = false
        setContent {
            MaterialTheme(scheme) {
                val state = remember { KGraphState(graph) }
                engine = rememberGraphEngine(state, runners)
                var selected by remember { mutableStateOf<tech.kloos.kompound.graph.runtime.Execution?>(null) }
                Box(Modifier.size(500.dp, 400.dp)) { KExecutionList(engine!!, selected, { selected = it; picked = it; pickedSomething = true }) }
            }
        }
        waitUntil(timeoutMillis = 5_000) { engine?.executions?.firstOrNull()?.finishedAt != null }
        waitForIdle()
        onNodeWithText("Live").assertIsDisplayed()
        onNodeWithText("#1 edit").assertIsDisplayed()
        onNodeWithText("#1 edit").performClick()
        waitForIdle()
        assertTrue(pickedSomething)
        assertEquals(1, picked?.id)
    }
}
