package tech.kloos.kompound.graph.runtime

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import tech.kloos.kompound.graph.model.Edge
import tech.kloos.kompound.graph.model.EdgeId
import tech.kloos.kompound.graph.model.Graph
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.rerouteNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEngineTest {
    private fun node(id: String, kind: String, data: Any? = null, ins: List<String> = emptyList()) =
        GraphNode(NodeId(id), kind, Offset.Zero, ins.map { PortSpec.input(it) } + PortSpec.output("out"), data)

    private fun wire(from: String, to: String, port: String = "a") =
        Edge(EdgeId("$from->$to.$port"), tech.kloos.kompound.graph.model.PortRef(NodeId(from), tech.kloos.kompound.graph.model.PortId("out")), tech.kloos.kompound.graph.model.PortRef(NodeId(to), tech.kloos.kompound.graph.model.PortId(port)))

    /** Slow source (`delay` ms from data), add (a + b), fail. */
    private fun TestScope.engine(autoRun: Boolean = true, log: MutableList<String> = mutableListOf(), concurrency: Int = 4) = GraphEngine(
        this,
        mapOf(
            "const" to singleOutputRunner { n, _ -> n.data },
            "slow" to singleOutputRunner { n, _ ->
                val (ms, value) = n.data as Pair<*, *>
                log += "start ${n.id}"
                try { delay(ms as Long) } catch (e: CancellationException) { log += "cancel ${n.id}"; throw e }
                log += "end ${n.id}"
                value
            },
            "add" to singleOutputRunner { _, i -> (i["a"] as Int) + (i["b"] as Int) },
            "boom" to singleOutputRunner { _, _ -> error("boom") },
        ),
        autoRun = autoRun,
        maxConcurrency = concurrency,
        runDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun resultsFlowDownstreamInDependencyOrder() = runTest {
        val e = engine()
        e.update(Graph.of(
            listOf(node("a", "const", 2), node("b", "const", 3), node("sum", "add", ins = listOf("a", "b")), node("twice", "add", ins = listOf("a", "b"))),
            listOf(wire("a", "sum", "a"), wire("b", "sum", "b"), wire("sum", "twice", "a"), wire("sum", "twice", "b")),
        ))
        advanceUntilIdle()
        assertEquals(5, e.output(NodeId("sum"), "out"))
        assertEquals(10, e.output(NodeId("twice"), "out"))
        assertTrue(e.runs.values.all { it is NodeRun.Done })
    }

    @Test
    fun aSuspendedNodeDelaysOnlyItsDownstreamAndTheResultArrivesLater() = runTest {
        val e = engine()
        e.update(Graph.of(
            listOf(node("fast", "const", 1), node("slow", "slow", 1000L to 10), node("sum", "add", ins = listOf("a", "b")), node("other", "const", 7)),
            listOf(wire("fast", "sum", "a"), wire("slow", "sum", "b")),
        ))
        advanceTimeBy(100)
        assertEquals(NodeRun.Running, e.runOf(NodeId("slow")))
        assertEquals(NodeRun.Waiting, e.runOf(NodeId("sum")))
        assertEquals(7, e.output(NodeId("other"), "out"), "independent branch is not held up")
        advanceTimeBy(1000)
        assertEquals(11, e.output(NodeId("sum"), "out"))
    }

    @Test
    fun independentNodesRunConcurrentlyUpToTheLimit() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log = log, concurrency = 2)
        e.update(Graph.of((1..4).map { node("s$it", "slow", 100L to it) }))
        advanceTimeBy(50)
        assertEquals(2, log.count { it.startsWith("start") })
        advanceUntilIdle()
        assertEquals(200L, currentTime, "two waves of two")
        assertEquals(4, log.count { it.startsWith("end") })
    }

    @Test
    fun changingASourceCancelsTheStaleRunAndRerunsDownstream() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log = log)
        val a = node("a", "slow", 1000L to 1)
        val b = node("b", "const", 5)
        val sum = node("sum", "add", ins = listOf("a", "b"))
        val edges = listOf(wire("a", "sum", "a"), wire("b", "sum", "b"))
        e.update(Graph.of(listOf(a, b, sum), edges))
        advanceTimeBy(500)
        e.update(Graph.of(listOf(a.copy(data = 200L to 2), b, sum), edges))
        advanceUntilIdle()
        assertTrue("cancel a" in log)
        assertEquals(7, e.output(NodeId("sum"), "out"), "uses the new value, never the cancelled one")
    }

    @Test
    fun movingANodeDoesNotRestartAnything() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log = log)
        val a = node("a", "slow", 100L to 1)
        e.update(Graph.of(listOf(a)))
        advanceUntilIdle()
        e.update(Graph.of(listOf(a.copy(position = Offset(50f, 50f)))))
        advanceUntilIdle()
        assertEquals(1, log.count { it == "start a" })
    }

    @Test
    fun aFailureBlocksDownstreamButNotOtherBranches() = runTest {
        val e = engine()
        e.update(Graph.of(
            listOf(node("bad", "boom"), node("ok", "const", 1), node("sum", "add", ins = listOf("a", "b")), node("after", "add", ins = listOf("a", "b"))),
            listOf(wire("bad", "sum", "a"), wire("ok", "sum", "b"), wire("sum", "after", "a"), wire("ok", "after", "b")),
        ))
        advanceUntilIdle()
        assertTrue((e.runOf(NodeId("bad")) as NodeRun.Failed).error.message == "boom")
        assertEquals(NodeRun.Blocked(NodeId("bad")), e.runOf(NodeId("sum")))
        assertEquals(NodeRun.Blocked(NodeId("bad")), e.runOf(NodeId("after")))
        assertEquals(1, e.output(NodeId("ok"), "out"))
    }

    @Test
    fun cyclesFailInsteadOfHanging() = runTest {
        val e = engine()
        e.update(Graph.of(
            listOf(node("x", "add", ins = listOf("a")), node("y", "add", ins = listOf("a")), node("free", "const", 1)),
            listOf(wire("x", "y"), wire("y", "x")),
        ))
        advanceUntilIdle()
        assertTrue((e.runOf(NodeId("x")) as NodeRun.Failed).error is CycleException)
        assertTrue(e.runOf(NodeId("y")) is NodeRun.Failed)
        assertTrue(e.runOf(NodeId("free")) is NodeRun.Done)
    }

    @Test
    fun manualModeWaitsForStartAndStopCancelsWhatRuns() = runTest {
        val log = mutableListOf<String>()
        val e = engine(autoRun = false, log = log)
        e.update(Graph.of(listOf(node("a", "slow", 1000L to 1))))
        advanceUntilIdle()
        assertEquals(NodeRun.Idle, e.runOf(NodeId("a")))
        e.start()
        advanceTimeBy(100)
        assertEquals(NodeRun.Running, e.runOf(NodeId("a")))
        e.stop()
        advanceUntilIdle()
        assertTrue("cancel a" in log)
        assertEquals(NodeRun.Idle, e.runOf(NodeId("a")))
        assertTrue(!e.isBusy)
    }

    @Test
    fun rerunRecomputesANodeAndEverythingAfterIt() = runTest {
        var calls = 0
        val e = GraphEngine(
            this,
            mapOf("count" to singleOutputRunner { _, _ -> ++calls }, "add" to singleOutputRunner { _, i -> (i["a"] as Int) * 10 }),
            runDispatcher = StandardTestDispatcher(testScheduler),
        )
        e.update(Graph.of(listOf(node("c", "count"), node("m", "add", ins = listOf("a"))), listOf(wire("c", "m"))))
        advanceUntilIdle()
        assertEquals(10, e.output(NodeId("m"), "out"))
        e.rerun(NodeId("c"))
        advanceUntilIdle()
        assertEquals(20, e.output(NodeId("m"), "out"))
    }

    @Test
    fun removingANodeDropsItsStateAndInvalidatesWhatItFed() = runTest {
        val e = engine()
        val a = node("a", "const", 1)
        val m = node("m", "add", ins = listOf("a", "b"))
        val b = node("b", "const", 2)
        e.update(Graph.of(listOf(a, b, m), listOf(wire("a", "m", "a"), wire("b", "m", "b"))))
        advanceUntilIdle()
        e.update(Graph.of(listOf(b, m), listOf()))
        advanceUntilIdle()
        assertTrue(NodeId("a") !in e.runs)
        assertTrue(e.runOf(NodeId("m")) is NodeRun.Failed, "add needs input a, which is gone: ${e.runOf(NodeId("m"))}")
    }

    @Test
    fun reroutesPassTheirValueAndUnknownKindsFail() = runTest {
        val e = engine()
        e.update(Graph.of(
            listOf(node("a", "const", 4), rerouteNode("r", Offset.Zero), node("mystery", "nope")),
            listOf(Edge(EdgeId("e"), tech.kloos.kompound.graph.model.PortRef(NodeId("a"), tech.kloos.kompound.graph.model.PortId("out")), tech.kloos.kompound.graph.model.PortRef(NodeId("r"), tech.kloos.kompound.graph.model.PortId("in")))),
        ))
        advanceUntilIdle()
        assertEquals(4, e.output(NodeId("r"), "out"))
        assertTrue((e.runOf(NodeId("mystery")) as NodeRun.Failed).error is MissingRunnerException)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEngineSubgraphTest {
    private val num = tech.kloos.kompound.graph.model.PortType.of("n")

    private fun real(id: String, kind: String, data: Any? = null, ins: List<String> = emptyList(), scope: String? = null) =
        GraphNode(NodeId(id), kind, Offset.Zero, ins.map { PortSpec.input(it, type = num) } + PortSpec.output("out", type = num), data, scope = scope?.let { NodeId(it) })

    private fun ref(node: String, port: String) = tech.kloos.kompound.graph.model.PortRef(NodeId(node), tech.kloos.kompound.graph.model.PortId(port))

    private fun wire(from: PortRefLike, to: PortRefLike) = Edge(EdgeId("${from.node}.${from.port}->${to.node}.${to.port}"), ref(from.node, from.port), ref(to.node, to.port))

    private class PortRefLike(val node: String, val port: String)

    private infix fun String.at(port: String) = PortRefLike(this, port)

    private fun TestScope.engine(log: MutableList<String> = mutableListOf()) = GraphEngine(
        this,
        mapOf(
            "const" to singleOutputRunner { n, _ -> n.data },
            "slow" to singleOutputRunner { n, i ->
                log += "start ${n.id}"
                delay(n.data as Long)
                i["a"]
            },
            "inc" to singleOutputRunner { _, i -> (i["a"] as Int) + 1 },
            "boom" to singleOutputRunner { _, _ -> error("boom") },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
    )

    /** Top level: src -> S(in1 .. out1) -> sink. Inside S: boundary in -> inc -> boundary out. */
    private fun oneLevel(innerKind: String = "inc", src: Int = 1): Graph {
        val s = NodeId("S")
        val subgraph = GraphNode(s, "subgraph", Offset.Zero, listOf(PortSpec.input("in1"), PortSpec.output("out1")), "S")
        val bin = GraphNode(NodeId("S/in/in1"), "subgraph.input", Offset.Zero, listOf(PortSpec.output("value")), "in1", scope = s)
        val bout = GraphNode(NodeId("S/out/out1"), "subgraph.output", Offset.Zero, listOf(PortSpec.input("value")), "out1", scope = s)
        return Graph.of(
            listOf(real("src", "const", src), subgraph, bin, real("inner", innerKind, ins = listOf("a"), scope = "S"), bout, real("sink", "inc", ins = listOf("a"))),
            listOf(
                wire("src" at "out", "S" at "in1"),
                wire("S/in/in1" at "value", "inner" at "a"),
                wire("inner" at "out", "S/out/out1" at "value"),
                wire("S" at "out1", "sink" at "a"),
            ),
        )
    }

    @Test
    fun valuesFlowThroughASubgraph() = runTest {
        val e = engine()
        e.update(oneLevel())
        advanceUntilIdle()
        assertEquals(2, e.output(NodeId("inner"), "out"), "boundary passes the outer value in")
        assertEquals(3, e.output(NodeId("sink"), "out"), "and the inner result out")
        assertTrue(NodeId("S") !in e.runs && NodeId("S/in/in1") !in e.runs, "virtual nodes do not run")
        assertEquals(mapOf(tech.kloos.kompound.graph.model.PortId("out1") to 2), (e.runOf(NodeId("S")) as NodeRun.Done).outputs)
        assertEquals(1, e.output(NodeId("S/in/in1"), "value"))
    }

    @Test
    fun nestedSubgraphsRouteThroughEveryLevel() = runTest {
        // outer O contains inner I; inside I: boundary -> inc -> boundary. O just forwards I.
        val o = NodeId("O")
        val i = NodeId("I")
        val g = Graph.of(
            listOf(
                real("src", "const", 10),
                GraphNode(o, "subgraph", Offset.Zero, listOf(PortSpec.input("in1"), PortSpec.output("out1")), "O"),
                GraphNode(NodeId("O/in/in1"), "subgraph.input", Offset.Zero, listOf(PortSpec.output("value")), "x", scope = o),
                GraphNode(i, "subgraph", Offset.Zero, listOf(PortSpec.input("in1"), PortSpec.output("out1")), "I", scope = o),
                GraphNode(NodeId("I/in/in1"), "subgraph.input", Offset.Zero, listOf(PortSpec.output("value")), "x", scope = i),
                real("deep", "inc", ins = listOf("a"), scope = "I"),
                GraphNode(NodeId("I/out/out1"), "subgraph.output", Offset.Zero, listOf(PortSpec.input("value")), "y", scope = i),
                GraphNode(NodeId("O/out/out1"), "subgraph.output", Offset.Zero, listOf(PortSpec.input("value")), "y", scope = o),
                real("sink", "inc", ins = listOf("a")),
            ),
            listOf(
                wire("src" at "out", "O" at "in1"),
                wire("O/in/in1" at "value", "I" at "in1"),
                wire("I/in/in1" at "value", "deep" at "a"),
                wire("deep" at "out", "I/out/out1" at "value"),
                wire("I" at "out1", "O/out/out1" at "value"),
                wire("O" at "out1", "sink" at "a"),
            ),
        )
        val e = engine()
        e.update(g)
        advanceUntilIdle()
        assertEquals(11, e.output(NodeId("deep"), "out"))
        assertEquals(12, e.output(NodeId("sink"), "out"))
        assertTrue(e.runOf(NodeId("O")) is NodeRun.Done && e.runOf(NodeId("I")) is NodeRun.Done)
    }

    @Test
    fun aSubgraphReportsTheStateOfWhatIsInsideAndHoldsBackWhatFollows() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        e.update(oneLevel("slow").let { g -> g.withNode(g.node(NodeId("inner"))!!.copy(data = 500L)) })
        advanceTimeBy(100)
        assertEquals(NodeRun.Running, e.runOf(NodeId("S")))
        assertEquals(NodeRun.Waiting, e.runOf(NodeId("sink")))
        advanceUntilIdle()
        assertTrue(e.runOf(NodeId("S")) is NodeRun.Done)
        assertEquals(2, e.output(NodeId("sink"), "out"))
    }

    @Test
    fun aFailureInsideBlocksTheNodesAfterTheSubgraph() = runTest {
        val e = engine()
        e.update(oneLevel("boom"))
        advanceUntilIdle()
        assertTrue(e.runOf(NodeId("S")) is NodeRun.Failed)
        assertEquals(NodeRun.Blocked(NodeId("inner")), e.runOf(NodeId("sink")))
    }

    @Test
    fun editingInsideASubgraphRerunsWhatIsDownstreamOutside() = runTest {
        val e = engine()
        val g = oneLevel()
        e.update(g)
        advanceUntilIdle()
        assertEquals(3, e.output(NodeId("sink"), "out"))
        e.update(g.withNode(g.node(NodeId("src"))!!.copy(data = 5)))
        advanceUntilIdle()
        assertEquals(7, e.output(NodeId("sink"), "out"))
        // Rewiring through the boundary (bypass the inner node) changes what the sink receives.
        val bypass = g.withoutEdges(setOf(EdgeId("inner.out->S/out/out1.value")))
            .withEdge(wire("S/in/in1" at "value", "S/out/out1" at "value"))
        e.update(bypass)
        advanceUntilIdle()
        assertEquals(2, e.output(NodeId("sink"), "out"), "boundary input wired straight to boundary output: 1 + 1")
    }

    @Test
    fun anUnwiredSubgraphInputOrOutputJustReadsAsNotConnected() = runTest {
        val e = engine()
        val g = oneLevel()
        e.update(g.withoutEdges(setOf(EdgeId("src.out->S.in1"))))
        advanceUntilIdle()
        assertTrue(e.runOf(NodeId("inner")) is NodeRun.Failed, "inc needs input a: ${e.runOf(NodeId("inner"))}")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEngineTraceTest {
    private fun node(id: String, kind: String, data: Any? = null, ins: List<String> = emptyList()) =
        GraphNode(NodeId(id), kind, Offset.Zero, ins.map { PortSpec.input(it) } + PortSpec.output("out"), data)

    private fun wire(from: String, to: String, port: String = "a") = Edge(
        EdgeId("$from->$to.$port"),
        tech.kloos.kompound.graph.model.PortRef(NodeId(from), tech.kloos.kompound.graph.model.PortId("out")),
        tech.kloos.kompound.graph.model.PortRef(NodeId(to), tech.kloos.kompound.graph.model.PortId(port)),
    )

    private fun TestScope.engine(options: TraceOptions = TraceOptions(), autoRun: Boolean = true) = GraphEngine(
        this,
        mapOf(
            "const" to singleOutputRunner { n, _ -> n.data },
            "work" to NodeRunner { ctx ->
                ctx.log("starting with ${ctx.inputs["a"]}")
                ctx.progress(0f, "begin")
                delay(100)
                ctx.progress(0.5f)
                ctx.emit("out", "partial")
                delay(100)
                ctx.log("careful", LogLevel.Warn)
                mapOf("out" to "${ctx.inputs["a"]}!")
            },
            "boom" to singleOutputRunner { _, _ -> error("boom") },
        ),
        autoRun = autoRun,
        runDispatcher = StandardTestDispatcher(testScheduler),
        trace = options,
        clock = { testScheduler.currentTime },
    )

    private val chain = Graph.of(
        listOf(node("a", "const", "x"), node("w", "work", ins = listOf("a"))),
        listOf(wire("a", "w")),
    )

    @Test
    fun anExecutionRecordsInputsOutputsLogsProgressAndEmissions() = runTest {
        val e = engine()
        e.update(chain)
        advanceTimeBy(50)
        val running = e.currentRun!!
        val attempt = running.latest(NodeId("w"))!!
        assertEquals(TraceStatus.Running, attempt.status)
        assertEquals(TraceTrigger.Auto, running.trigger)
        assertEquals(mapOf(tech.kloos.kompound.graph.model.PortId("a") to "x"), attempt.inputs)
        assertEquals("starting with x", attempt.logs.single().message)
        assertEquals("begin", attempt.progressMessage)
        advanceUntilIdle()
        assertEquals(null, e.currentRun)
        val done = e.executions.single()
        assertEquals(TraceStatus.Succeeded, done.status)
        val w = done.latest(NodeId("w"))!!
        assertEquals(TraceStatus.Succeeded, w.status)
        assertEquals(mapOf(tech.kloos.kompound.graph.model.PortId("out") to "x!"), w.outputs, "returned value wins over the emission")
        assertEquals("partial", w.emissions.single().value)
        assertEquals(listOf(LogLevel.Info, LogLevel.Warn), w.logs.map { it.level })
        assertEquals(200L, w.durationMillis)
        assertEquals(0.5f, w.progress)
        assertEquals(listOf("a", "w"), done.attempts.map { it.nodeId.value })
        assertEquals(w, e.lastAttempt(NodeId("w")))
    }

    @Test
    fun aStaleRunBecomesACancelledAttemptAndTheRestartIsTheNextOne() = runTest {
        val e = engine()
        e.update(chain)
        advanceTimeBy(50)
        e.update(Graph.of(listOf(node("a", "const", "y"), node("w", "work", ins = listOf("a"))), listOf(wire("a", "w"))))
        advanceUntilIdle()
        val execution = e.executions.single()
        val attempts = execution.attemptsOf(NodeId("w"))
        assertEquals(listOf(TraceStatus.Cancelled, TraceStatus.Succeeded), attempts.map { it.status })
        assertEquals(listOf(1, 2), attempts.map { it.number })
        assertEquals("y!", attempts.last().outputs!![tech.kloos.kompound.graph.model.PortId("out")])
        assertEquals(TraceStatus.Succeeded, execution.status, "latest attempts decide")
    }

    @Test
    fun failuresAreRecordedAndFailTheExecution() = runTest {
        val e = engine()
        e.update(Graph.of(listOf(node("b", "boom"))))
        advanceUntilIdle()
        val ex = e.executions.single()
        assertEquals(TraceStatus.Failed, ex.status)
        assertEquals("boom", ex.latest(NodeId("b"))!!.error!!.message)
    }

    @Test
    fun everyBusyPeriodIsItsOwnExecutionWithItsTriggerAndHistoryIsBounded() = runTest {
        val e = engine(TraceOptions(maxExecutions = 3), autoRun = false)
        e.update(Graph.of(listOf(node("a", "const", 1))))
        advanceUntilIdle()
        assertTrue(e.executions.isEmpty(), "nothing ran yet")
        e.start()
        advanceUntilIdle()
        repeat(4) { e.rerun(NodeId("a")); advanceUntilIdle() }
        assertEquals(3, e.executions.size)
        assertEquals(listOf(3, 4, 5), e.executions.map { it.id })
        assertEquals(setOf(TraceTrigger.Rerun), e.executions.map { it.trigger }.toSet())
    }

    @Test
    fun stoppingMarksTheExecutionAndItsRunningAttemptsCancelled() = runTest {
        val e = engine(autoRun = false)
        e.update(chain)
        e.start()
        advanceTimeBy(50)
        e.stop()
        advanceUntilIdle()
        val ex = e.executions.single()
        assertEquals(TraceStatus.Cancelled, ex.status)
        assertEquals(TraceTrigger.Manual, ex.trigger)
        assertEquals(TraceStatus.Cancelled, ex.latest(NodeId("w"))!!.status)
        assertEquals(null, e.currentRun)
    }

    @Test
    fun redactionAndCaptureOffKeepValuesOutOfTheTraceButNotOutOfTheGraph() = runTest {
        val redacting = engine(TraceOptions(redact = { _, _, v -> if (v == "x") "***" else v }))
        redacting.update(chain)
        advanceUntilIdle()
        val w = redacting.executions.single().latest(NodeId("w"))!!
        assertEquals("***", w.inputs.values.single())
        assertEquals("x!", redacting.output(NodeId("w"), "out"), "the real value flowed on")

        val silent = engine(TraceOptions(captureValues = false))
        silent.update(chain)
        advanceUntilIdle()
        val s = silent.executions.single().latest(NodeId("w"))!!
        assertTrue(!s.valuesCaptured && s.inputs.isEmpty() && s.outputs!!.isEmpty() && s.emissions.isEmpty())
        assertEquals("starting with x", s.logs.first().message, "logs are still kept")
        assertEquals("x!", silent.output(NodeId("w"), "out"))
    }
}
