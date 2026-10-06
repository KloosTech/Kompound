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
import tech.kloos.kompound.graph.model.KSecret
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.model.PortRef
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.PortType
import tech.kloos.kompound.json.JsonNull
import tech.kloos.kompound.json.JsonNumber
import tech.kloos.kompound.json.JsonObject
import tech.kloos.kompound.json.JsonString
import tech.kloos.kompound.json.MissingFieldException
import tech.kloos.kompound.json.TemplateScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateRenderTest {
    private fun source(id: String, data: Any?) = GraphNode(NodeId(id), "const", Offset.Zero, listOf(PortSpec.output("out", type = PortType.json())), data)

    private fun consumer(vararg inputs: String, body: String) =
        GraphNode(NodeId("c"), "render", Offset.Zero, inputs.map { PortSpec.input(it) } + PortSpec.output("out"), body)

    private fun wire(from: String, to: String, port: String) =
        Edge(EdgeId("$from->$port"), PortRef(NodeId(from), PortId("out")), PortRef(NodeId(to), PortId(port)))

    private fun TestScope.renderEngine(extra: Array<TemplateScope> = emptyArray()) = GraphEngine(
        this,
        mapOf(
            "const" to singleOutputRunner { n, _ -> n.data },
            "render" to NodeRunner { ctx -> mapOf("out" to ctx.render(ctx.node.data as String, *extra)) },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
    )

    private val producerJson = """{"title":"T","description":"D","tasks":[{"name":"x"}]}"""

    @Test
    fun aRunnerRendersItsSettingWithTheOneJsonInput() = runTest {
        val e = renderEngine()
        e.update(Graph.of(listOf(source("p", producerJson), consumer("in", body = """{"a": "{{title}}", "b": "{{description}}"}""")), listOf(wire("p", "c", "in"))))
        advanceUntilIdle()
        assertEquals("""{"a": "T", "b": "D"}""", e.output(NodeId("c"), "out"))
    }

    @Test
    fun aMisspelledFieldFailsTheRunNamingNodePortAndPath() = runTest {
        val e = renderEngine()
        e.update(Graph.of(listOf(source("p", producerJson), consumer("in", body = "{{tilte}}")), listOf(wire("p", "c", "in"))))
        advanceUntilIdle()
        val failure = (e.runOf(NodeId("c")) as NodeRun.Failed).error as MissingFieldException
        assertEquals("tilte", failure.path)
        assertEquals("c", failure.node)
        assertEquals("in", failure.port)
        assertTrue("title" in failure.message!! && "description" in failure.message!!, "the message lists what the input has: ${failure.message}")
    }

    @Test
    fun severalJsonInputsAreReachedByPortId() = runTest {
        val e = renderEngine()
        e.update(Graph.of(
            listOf(source("p", """{"title":"T"}"""), source("q", """{"title":"Q","n":7}"""), consumer("a", "b", body = "{{a.title}} {{b.title}} {{b.n}}")),
            listOf(wire("p", "c", "a"), wire("q", "c", "b")),
        ))
        advanceUntilIdle()
        assertEquals("T Q 7", e.output(NodeId("c"), "out"))
        // a bare path is ambiguous with two structured inputs: it is missing, and the error says which path
        val bare = renderEngine()
        bare.update(Graph.of(
            listOf(source("p", """{"title":"T"}"""), source("q", """{"title":"Q"}"""), consumer("a", "b", body = "{{title}}")),
            listOf(wire("p", "c", "a"), wire("q", "c", "b")),
        ))
        advanceUntilIdle()
        val failure = (bare.runOf(NodeId("c")) as NodeRun.Failed).error as MissingFieldException
        assertEquals("title", failure.path)
        assertEquals("c", failure.node)
    }

    @Test
    fun scalarInputsAreReachedByName_andAnArrayInputByIndexOrStar() = runTest {
        val e = renderEngine()
        e.update(Graph.of(
            listOf(source("n", 5), source("t", """[{"id":1},{"id":2}]"""), consumer("n", "t", body = "{{n}} {{t[1].id}} {{t[*].id}}")),
            listOf(wire("n", "c", "n"), wire("t", "c", "t")),
        ))
        advanceUntilIdle()
        assertEquals("5 2 [1,2]", e.output(NodeId("c"), "out"))
    }

    @Test
    fun extraScopesAreAskedAfterTheInputsAndASecretIsUsable() = runTest {
        val vars = TemplateScope.ofNamed(mapOf("env" to mapOf("HOST" to "h.example")))
        val e = renderEngine(arrayOf(vars))
        e.update(Graph.of(
            listOf(source("p", """{"title":"T"}"""), source("k", KSecret("sekret")), consumer("in", "key", body = "https://{{env.HOST}}/{{title}}?k={{key}}")),
            listOf(wire("p", "c", "in"), wire("k", "c", "key")),
        ))
        advanceUntilIdle()
        assertEquals("https://h.example/T?k=sekret", e.output(NodeId("c"), "out"))
        // the trace never shows the secret input
        val stored = e.executions.last().latest(NodeId("c"))!!.inputs[PortId("key")]
        assertEquals(KSecret.Hidden, stored)
    }

    @Test
    fun nodeInputsAsJson() {
        val inputs = NodeInputs(mapOf(
            PortId("obj") to """{"a":1}""", PortId("text") to "plain", PortId("n") to 2, PortId("nothing") to null,
            PortId("list") to listOf(1, "x"), PortId("secret") to KSecret("s"), PortId("weird") to Any(),
        ))
        assertEquals(1.0, ((inputs.json("obj") as JsonObject)["a"] as JsonNumber).value)
        assertEquals(JsonString("plain"), inputs.json("text"))
        assertEquals(JsonNumber(2.0), inputs.json("n"))
        assertEquals(JsonNull, inputs.json("nothing"))
        assertEquals("""[1,"x"]""", inputs.json("list")!!.toJson())
        assertEquals(JsonString("s"), inputs.json("secret"))
        assertNull(inputs.json("weird"))
        assertNull(inputs.json("not-connected"))
    }
}
