package tech.kloos.kompound.graph.runtime

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEngineSignalTest {
    private fun ticker(id: String, count: Int) =
        GraphNode(NodeId(id), "ticker", Offset.Zero, listOf(PortSpec.output("out")), count)

    private fun worker(id: String, mode: tech.kloos.kompound.graph.model.SignalMode, kind: String = "double") =
        GraphNode(NodeId(id), kind, Offset.Zero, listOf(PortSpec.input("a", signal = mode), PortSpec.output("out")))

    private fun wire(from: String, to: String, port: String = "a") = Edge(
        EdgeId("$from->$to.$port"),
        tech.kloos.kompound.graph.model.PortRef(NodeId(from), tech.kloos.kompound.graph.model.PortId("out")),
        tech.kloos.kompound.graph.model.PortRef(NodeId(to), tech.kloos.kompound.graph.model.PortId(port)),
    )

    private val mode = tech.kloos.kompound.graph.model.SignalMode.entries.associateBy { it.name }

    private fun TestScope.engine(log: MutableList<String> = mutableListOf()) = GraphEngine(
        this,
        mapOf(
            // emits 1..n, one every 100 ms, returns nothing
            "ticker" to NodeRunner { ctx ->
                repeat(ctx.node.data as Int) { i -> delay(100); ctx.emit("out", i + 1) }
                emptyMap()
            },
            "double" to singleOutputRunner { _, i -> delay(150); (i["a"] as Int) * 2 },
            "failOnTwo" to singleOutputRunner { _, i -> if (i["a"] == 2) error("two") else i["a"] },
            "sum" to singleOutputRunner { n, i -> log += "sum ${n.id} ${i["a"]}"; (i["a"] as List<*>).sumOf { it as Int } },
            "last" to singleOutputRunner { n, i -> log += "last ${n.id} ${i["a"]}"; i["a"] },
            "add" to singleOutputRunner { _, i -> (i["a"] as Int) + (i["b"] as Int) },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun run(vararg nodes: GraphNode, edges: List<Edge>) = Graph.of(nodes.toList(), edges)

    @Test
    fun latestRestartsOnEveryNewValueAndOnlyTheNewestRunSurvives() = runTest {
        val e = engine()
        e.update(run(ticker("t", 3), worker("w", mode.getValue("Latest")), edges = listOf(wire("t", "w"))))
        advanceTimeBy(150)
        assertEquals(1, e.signalCount(NodeId("t"), "out"))
        assertEquals(1, e.latest(NodeId("t"), "out"))
        advanceUntilIdle()
        val attempts = e.executions.single().attemptsOf(NodeId("w"))
        assertEquals(listOf(TraceStatus.Cancelled, TraceStatus.Cancelled, TraceStatus.Succeeded), attempts.map { it.status })
        assertEquals(listOf(1, 2, 3), attempts.map { it.inputs.values.single() })
        assertEquals(6, e.output(NodeId("w"), "out"))
        assertEquals(1, e.signalCount(NodeId("w"), "out"), "only the surviving run produced a value")
    }

    @Test
    fun eachProcessesEveryValueInOrderOneAfterTheOther() = runTest {
        val e = engine()
        e.update(run(ticker("t", 3), worker("w", mode.getValue("Each")), edges = listOf(wire("t", "w"))))
        advanceUntilIdle()
        val attempts = e.executions.single().attemptsOf(NodeId("w"))
        assertEquals(listOf(1, 2, 3), attempts.map { it.inputs.values.single() })
        assertTrue(attempts.all { it.status == TraceStatus.Succeeded })
        assertEquals(3, e.signalCount(NodeId("w"), "out"))
        assertEquals(6, e.latest(NodeId("w"), "out"))
        assertEquals(550L, currentTime, "100 + 3 sequential runs of 150")
        assertTrue(attempts.zipWithNext().all { (a, b) -> b.startedAt >= a.finishedAt!! }, "never two at once")
        assertTrue(e.runOf(NodeId("w")) is NodeRun.Done)
    }

    @Test
    fun collectWaitsForTheEndAndGetsEveryValue() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        e.update(run(ticker("t", 3), worker("s", mode.getValue("Collect"), "sum"), edges = listOf(wire("t", "s"))))
        advanceTimeBy(250)
        assertEquals(NodeRun.Waiting, e.runOf(NodeId("s")))
        assertTrue(log.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("sum s [1, 2, 3]"), log)
        assertEquals(6, e.output(NodeId("s"), "out"))
    }

    @Test
    fun finalWaitsForTheEndAndGetsTheLastValue() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        e.update(run(ticker("t", 3), worker("l", mode.getValue("Final"), "last"), edges = listOf(wire("t", "l"))))
        advanceUntilIdle()
        assertEquals(listOf("last l 3"), log)
    }

    @Test
    fun aNodeWithTwoLatestInputsWaitsForBothThenFollowsEither() = runTest {
        val e = engine()
        val add = GraphNode(NodeId("add"), "add", Offset.Zero, listOf(PortSpec.input("a"), PortSpec.input("b"), PortSpec.output("out")))
        e.update(run(ticker("x", 2), ticker("y", 1), add, edges = listOf(wire("x", "add", "a"), wire("y", "add", "b"))))
        advanceTimeBy(50)
        assertEquals(NodeRun.Waiting, e.runOf(NodeId("add")))
        advanceUntilIdle()
        val inputs = e.executions.single().attemptsOf(NodeId("add")).map { it.inputs.values.toList() }
        assertEquals(listOf(listOf(1, 1), listOf(2, 1)), inputs, "first run needs both; the next follows x")
        assertEquals(3, e.output(NodeId("add"), "out"))
    }

    @Test
    fun valuesStreamThroughReroutesAndSubgraphBoundaries() = runTest {
        val e = engine()
        val reroute = tech.kloos.kompound.graph.rerouteNode("r", Offset.Zero)
        val g = run(
            ticker("t", 3), reroute, worker("w", mode.getValue("Each")),
            edges = listOf(
                Edge(EdgeId("a"), tech.kloos.kompound.graph.model.PortRef(NodeId("t"), tech.kloos.kompound.graph.model.PortId("out")), tech.kloos.kompound.graph.model.PortRef(NodeId("r"), tech.kloos.kompound.graph.model.PortId("in"))),
                Edge(EdgeId("b"), tech.kloos.kompound.graph.model.PortRef(NodeId("r"), tech.kloos.kompound.graph.model.PortId("out")), tech.kloos.kompound.graph.model.PortRef(NodeId("w"), tech.kloos.kompound.graph.model.PortId("a"))),
            ),
        )
        e.update(g)
        advanceUntilIdle()
        assertEquals(3, e.signalCount(NodeId("w"), "out"))
        assertEquals(6, e.output(NodeId("w"), "out"))
    }

    @Test
    fun aFailureInTheMiddleOfAStreamBlocksWhatFollows() = runTest {
        val e = engine()
        e.update(run(
            ticker("t", 3), worker("f", mode.getValue("Each"), "failOnTwo"), worker("after", mode.getValue("Latest")),
            edges = listOf(wire("t", "f"), wire("f", "after")),
        ))
        advanceUntilIdle()
        assertTrue(e.runOf(NodeId("f")) is NodeRun.Failed)
        assertEquals(NodeRun.Blocked(NodeId("f")), e.runOf(NodeId("after")))
        assertEquals(TraceStatus.Failed, e.executions.single().status)
    }

    @Test
    fun editingTheSourceMidStreamDropsEverythingItProducedAndStartsOver() = runTest {
        val e = engine()
        val consumer = worker("w", mode.getValue("Each"))
        e.update(run(ticker("t", 3), consumer, edges = listOf(wire("t", "w"))))
        advanceTimeBy(250)
        assertEquals(2, e.signalCount(NodeId("t"), "out"))
        e.update(run(ticker("t", 2), consumer, edges = listOf(wire("t", "w"))))
        assertEquals(0, e.signalCount(NodeId("t"), "out"))
        advanceUntilIdle()
        assertEquals(2, e.signalCount(NodeId("t"), "out"))
        assertEquals(4, e.latest(NodeId("w"), "out"))
    }

    @Test
    fun aNodeThatEmitsNothingStillGivesDownstreamOneNullValue() = runTest {
        val e = GraphEngine(
            this,
            mapOf("silent" to NodeRunner { emptyMap() }, "echo" to singleOutputRunner { _, i -> if (i.has("a")) "got ${i["a"]}" else "none" }),
            runDispatcher = StandardTestDispatcher(testScheduler),
        )
        val silent = GraphNode(NodeId("s"), "silent", Offset.Zero, listOf(PortSpec.output("out")))
        e.update(run(silent, worker("e", mode.getValue("Latest"), "echo"), edges = listOf(wire("s", "e"))))
        advanceUntilIdle()
        assertEquals("got null", e.output(NodeId("e"), "out"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEnginePinAndTestTest {
    private val out = tech.kloos.kompound.graph.model.PortId("out")

    private fun node(id: String, kind: String, data: Any? = null, ins: List<String> = emptyList()) =
        GraphNode(NodeId(id), kind, Offset.Zero, ins.map { PortSpec.input(it) } + PortSpec.output("out"), data)

    private fun wire(from: String, to: String, port: String = "a") = Edge(
        EdgeId("$from->$to.$port"),
        tech.kloos.kompound.graph.model.PortRef(NodeId(from), out),
        tech.kloos.kompound.graph.model.PortRef(NodeId(to), tech.kloos.kompound.graph.model.PortId(port)),
    )

    private fun TestScope.engine(log: MutableList<String> = mutableListOf()) = GraphEngine(
        this,
        mapOf(
            "slow" to singleOutputRunner { n, _ -> log += "slow ${n.id}"; delay(1000); n.data },
            "inc" to NodeRunner { ctx ->
                ctx.log("inc of ${ctx.inputs["a"]}")
                ctx.progress(0.5f)
                log += "inc ${ctx.node.id}"
                mapOf("out" to (ctx.inputs["a"] as Int) + 1)
            },
            "boom" to singleOutputRunner { _, _ -> error("boom") },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
    )

    private val chain = Graph.of(
        listOf(node("src", "slow", 5), node("mid", "inc", ins = listOf("a")), node("end", "inc", ins = listOf("a"))),
        listOf(wire("src", "mid"), wire("mid", "end")),
    )

    @Test
    fun aPinnedNodeSkipsItsRunnerAndFeedsDownstreamWithItsPin() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        val pinned = chain.withNode(chain.node(NodeId("mid"))!!.copy(pin = mapOf(out to 100)))
        e.update(pinned)
        advanceUntilIdle()
        assertEquals(101, e.output(NodeId("end"), "out"))
        assertTrue("inc mid" !in log, "pinned node did not run")
        assertEquals(mapOf(out to 100), (e.runOf(NodeId("mid")) as NodeRun.Done).outputs)
    }

    @Test
    fun nodesOnlyAPinnedNodeWouldHaveReadDoNotRunAtAll() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        e.update(chain.withNode(chain.node(NodeId("mid"))!!.copy(pin = mapOf(out to 100))))
        advanceUntilIdle()
        assertTrue(log.none { it.startsWith("slow") }, "the slow source was skipped: $log")
        assertEquals(NodeRun.Idle, e.runOf(NodeId("src")))
        assertEquals(0L, currentTime)
    }

    @Test
    fun pinningWhileRunningCancelsWhatIsNoLongerNeededAndUnpinningRunsItAgain() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        e.update(chain)
        advanceTimeBy(500)
        assertEquals(NodeRun.Running, e.runOf(NodeId("src")))
        val pinned = chain.withNode(chain.node(NodeId("mid"))!!.copy(pin = mapOf(out to 7)))
        e.update(pinned)
        assertEquals(NodeRun.Idle, e.runOf(NodeId("src")), "source no longer needed")
        advanceUntilIdle()
        assertEquals(8, e.output(NodeId("end"), "out"))
        e.update(chain)
        advanceUntilIdle()
        assertEquals(7, e.output(NodeId("end"), "out"), "5 + 1 + 1 after unpinning")
    }

    @Test
    fun changingAPinRerunsWhatFollows() = runTest {
        val e = engine()
        val base = chain.node(NodeId("mid"))!!
        e.update(chain.withNode(base.copy(pin = mapOf(out to 1))))
        advanceUntilIdle()
        assertEquals(2, e.output(NodeId("end"), "out"))
        e.update(chain.withNode(base.copy(pin = mapOf(out to 10))))
        advanceUntilIdle()
        assertEquals(11, e.output(NodeId("end"), "out"))
    }

    @Test
    fun aTestRunUsesStaticInputsAndLeavesTheGraphAlone() = runTest {
        val e = engine()
        e.update(chain)
        advanceUntilIdle()
        val before = e.output(NodeId("end"), "out")
        val test = e.testNode(NodeId("mid"), mapOf("a" to 41))!!
        assertEquals(TraceStatus.Running, test.status)
        advanceUntilIdle()
        assertEquals(TraceStatus.Succeeded, test.status)
        assertEquals(mapOf(out to 42), test.outputs)
        assertEquals(before, e.output(NodeId("end"), "out"), "the graph's own results are untouched")
        val run = e.executions.last()
        assertEquals(TraceTrigger.Test, run.trigger)
        assertEquals(TraceStatus.Succeeded, run.status)
        assertEquals("inc of 41", test.attempt.logs.single().message)
        assertEquals(0.5f, test.attempt.progress)
        assertEquals(41, test.attempt.inputs.values.single())
    }

    @Test
    fun aTestRunCanFailBeCancelledAndNeedsNoUpstream() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        e.update(chain)
        val failing = e.testNode(NodeId("mid"), emptyMap())!!
        advanceUntilIdle()
        assertEquals(TraceStatus.Failed, failing.status, "a is missing: the runner fails")
        assertTrue(failing.attempt.error != null)
        val slow = e.testNode(NodeId("src"), emptyMap())!!
        advanceTimeBy(100)
        slow.cancel()
        advanceUntilIdle()
        assertEquals(TraceStatus.Cancelled, slow.status)
        assertEquals(null, e.testNode(NodeId("nope"), emptyMap()))
    }

    @Test
    fun currentInputsShowWhatUpstreamProducedSoATestCanStartFromRealData() = runTest {
        val e = engine()
        e.update(chain)
        advanceUntilIdle()
        assertEquals(mapOf(tech.kloos.kompound.graph.model.PortId("a") to 5), e.currentInputs(NodeId("mid")))
        assertEquals(mapOf(tech.kloos.kompound.graph.model.PortId("a") to 6), e.currentInputs(NodeId("end")))
        assertEquals(emptyMap(), e.currentInputs(NodeId("src")))
    }

    @Test
    fun pinningFromTheEngineIsOneUndoStepOnTheEditorState() = runTest {
        val e = engine()
        val state = tech.kloos.kompound.graph.KGraphState(chain)
        e.update(state.graph)
        advanceUntilIdle()
        assertTrue(state.pinCurrentOutputs(e, NodeId("mid")))
        assertEquals(mapOf(out to 6), state.graph.node(NodeId("mid"))!!.pin)
        state.undo()
        assertEquals(null, state.graph.node(NodeId("mid"))!!.pin)
        state.redo()
        state.unpin(NodeId("mid"))
        assertEquals(null, state.graph.node(NodeId("mid"))!!.pin)
        assertTrue(!state.pinCurrentOutputs(e, NodeId("nope")))
    }
}

class GraphEngineThreadingTest {
    @Test
    fun aMultiThreadedScopeStillRunsEveryNodeExactlyOnce() = runTest {
        val leaves = (0 until 16).map { GraphNode(NodeId("c$it"), "const", Offset.Zero, listOf(PortSpec.output("out")), it) }
        // pairwise sums up to a single root
        val nodes = ArrayList<GraphNode>(leaves)
        val edges = ArrayList<Edge>()
        var level = leaves.map { it.id }
        var n = 0
        while (level.size > 1) {
            val next = ArrayList<NodeId>()
            for (pair in level.chunked(2)) {
                val id = NodeId("s${n++}")
                nodes += GraphNode(id, "add", Offset.Zero, listOf(PortSpec.input("a"), PortSpec.input("b"), PortSpec.output("out")))
                pair.forEachIndexed { i, from ->
                    edges += Edge(EdgeId("$from->$id"), tech.kloos.kompound.graph.model.PortRef(from, tech.kloos.kompound.graph.model.PortId("out")), tech.kloos.kompound.graph.model.PortRef(id, tech.kloos.kompound.graph.model.PortId(if (i == 0) "a" else "b")))
                }
                next += id
            }
            level = next
        }
        val root = level.single()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
        try {
            val engine = GraphEngine(
                scope,
                mapOf(
                    "const" to singleOutputRunner { node, _ -> delay(1); node.data },
                    "add" to singleOutputRunner { _, i -> delay(1); (i["a"] as Int) + (i["b"] as Int) },
                ),
                maxConcurrency = 8,
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                engine.update(Graph.of(nodes, edges))
                kotlinx.coroutines.withTimeout(10_000) { while (engine.runOf(root) !is NodeRun.Done) delay(5) }
            }
            assertEquals((0 until 16).sum(), engine.output(root, "out"))
            val execution = engine.executions.single()
            assertEquals(nodes.size, execution.attempts.size, "every node ran exactly once")
            assertTrue(execution.attempts.all { it.number == 1 && it.status == TraceStatus.Succeeded })
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEngineRunControlTest {
    private val out = tech.kloos.kompound.graph.model.PortId("out")

    private fun node(id: String, kind: String, data: Any? = null, ins: List<String> = emptyList()) =
        GraphNode(NodeId(id), kind, Offset.Zero, ins.map { PortSpec.input(it) } + PortSpec.output("out"), data)

    private fun wire(from: String, to: String) = Edge(
        EdgeId("$from->$to"),
        tech.kloos.kompound.graph.model.PortRef(NodeId(from), out),
        tech.kloos.kompound.graph.model.PortRef(NodeId(to), tech.kloos.kompound.graph.model.PortId("a")),
    )

    private val chain = Graph.of(
        listOf(node("src", "work", 1), node("mid", "work", ins = listOf("a"))),
        listOf(wire("src", "mid")),
    )

    private fun TestScope.engine(
        autoRun: Boolean,
        log: MutableList<String> = mutableListOf(),
        gate: ((GraphNode, TraceTrigger) -> Boolean)? = null,
    ) = GraphEngine(
        this,
        mapOf(
            "work" to singleOutputRunner { n, i -> log += "run ${n.id}"; delay(100); n.data ?: i["a"] },
            "boom" to singleOutputRunner { _, _ -> error("boom") },
        ),
        autoRun = autoRun,
        runDispatcher = StandardTestDispatcher(testScheduler),
        beforeRun = gate,
    )

    @Test
    fun manualModeOnlyMarksResultsStaleOnEditAndRunsOnceOnStart() = runTest {
        val log = mutableListOf<String>()
        val e = engine(autoRun = false, log = log)
        e.update(chain)
        advanceUntilIdle()
        assertTrue(log.isEmpty() && !e.isActive)
        e.start()
        assertTrue(e.isActive && e.isBusy)
        advanceUntilIdle()
        assertEquals(listOf("run src", "run mid"), log)
        assertTrue(!e.isActive && !e.isBusy, "back to idle once the run is over")
        // an edit afterwards invalidates (results go stale) but starts nothing, however often it happens
        repeat(3) { n -> e.update(Graph.of(listOf(node("src", "work", n + 10), chain.node(NodeId("mid"))!!), chain.edges.values.toList())) }
        advanceUntilIdle()
        assertEquals(2, log.size)
        assertEquals(NodeRun.Idle, e.runOf(NodeId("src")))
        assertEquals(NodeRun.Idle, e.runOf(NodeId("mid")))
        e.start()
        advanceUntilIdle()
        assertEquals(12, e.output(NodeId("mid"), "out"))
    }

    @Test
    fun rerunInManualModeIsAlsoOneShot() = runTest {
        val log = mutableListOf<String>()
        val e = engine(autoRun = false, log = log)
        e.update(chain)
        e.rerun(NodeId("src"))
        advanceUntilIdle()
        assertEquals(2, log.size)
        assertTrue(!e.isActive)
        e.update(Graph.of(listOf(node("src", "work", 5), chain.node(NodeId("mid"))!!), chain.edges.values.toList()))
        advanceUntilIdle()
        assertEquals(2, log.size, "no run on its own after the one-shot")
    }

    @Test
    fun stopKeepsFailedBlockedAndFinishedStatesButResetsUnfinishedNodes() = runTest {
        val e = GraphEngine(
            this,
            mapOf("boom" to singleOutputRunner { _, _ -> error("boom") }, "work" to singleOutputRunner { n, _ -> delay(1000); n.data }, "const" to singleOutputRunner { n, _ -> n.data }),
            runDispatcher = StandardTestDispatcher(testScheduler),
        )
        e.update(Graph.of(
            listOf(node("bad", "boom"), node("after", "const", ins = listOf("a")), node("ok", "const", 1), node("slow", "work", 2)),
            listOf(wire("bad", "after")),
        ))
        advanceTimeBy(100)
        e.stop()
        advanceUntilIdle()
        assertTrue(e.runOf(NodeId("bad")) is NodeRun.Failed, "error kept")
        assertEquals(NodeRun.Blocked(NodeId("bad")), e.runOf(NodeId("after")))
        assertTrue(e.runOf(NodeId("ok")) is NodeRun.Done)
        assertEquals(NodeRun.Idle, e.runOf(NodeId("slow")), "was running: starts over")
        assertTrue(!e.isActive && !e.isBusy)
    }

    @Test
    fun deactivateLetsTheRunFinishButStopsReactingToEdits() = runTest {
        val log = mutableListOf<String>()
        val e = engine(autoRun = true, log = log)
        e.update(chain)
        advanceTimeBy(50)
        e.deactivate()
        assertTrue(!e.isActive)
        advanceUntilIdle()
        assertTrue(e.runOf(NodeId("mid")) is NodeRun.Done, "the run in progress finished")
        e.update(Graph.of(listOf(node("src", "work", 7), chain.node(NodeId("mid"))!!), chain.edges.values.toList()))
        advanceUntilIdle()
        assertEquals(2, log.size, "edit did not start anything")
        e.start()
        assertTrue(e.isActive)
        advanceUntilIdle()
        assertEquals(7, e.output(NodeId("mid"), "out"))
    }

    @Test
    fun awaitIdleAndRunToCompletionReturnWhenTheWorkIsDone() = runTest {
        val e = engine(autoRun = false)
        e.update(chain)
        e.awaitIdle()   // nothing running: returns at once
        var done = false
        val job = launch { e.runToCompletion(); done = true }
        advanceTimeBy(50)
        assertTrue(!done && e.isBusy)
        advanceUntilIdle()
        job.join()
        assertTrue(done && !e.isBusy)
        assertEquals(1, e.output(NodeId("mid"), "out"))
    }

    @Test
    fun theGateDecidesPerNodeAndTriggerAndDeclinedNodesAreAskedAgainOnStart() = runTest {
        val log = mutableListOf<String>()
        val asked = mutableListOf<String>()
        val e = engine(autoRun = true, log = log, gate = { n, t -> asked += "${n.id}:$t"; t != TraceTrigger.Auto })
        e.update(chain)
        advanceUntilIdle()
        assertTrue(log.isEmpty(), "automatic run refused")
        assertEquals(NodeRun.Declined, e.runOf(NodeId("src")))
        assertEquals(NodeRun.Blocked(NodeId("src"), declined = true), e.runOf(NodeId("mid")), "blocked by a refusal, not by a failure")
        assertEquals(listOf("src:Auto"), asked, "asked once, not on every sweep")
        e.start()
        advanceUntilIdle()
        assertEquals(listOf("run src", "run mid"), log)
        assertEquals(listOf("src:Auto", "src:Manual", "mid:Manual"), asked)
        e.rerun(NodeId("mid"))
        advanceUntilIdle()
        assertEquals("mid:Rerun", asked.last())
    }

    @Test
    fun aGateThatRefusesTestsMakesTestNodeReturnNull() = runTest {
        val e = engine(autoRun = true, gate = { _, t -> t != TraceTrigger.Test })
        e.update(chain)
        advanceUntilIdle()
        assertEquals(null, e.testNode(NodeId("mid"), mapOf("a" to 1)))
        assertTrue(e.executions.none { it.trigger == TraceTrigger.Test })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GraphEngineBranchingTest {
    private val sm = tech.kloos.kompound.graph.model.SignalMode.Latest

    private fun node(id: String, kind: String, data: Any? = null, ins: List<Pair<String, tech.kloos.kompound.graph.model.SignalMode>> = emptyList(), outs: List<String> = listOf("out")) =
        GraphNode(NodeId(id), kind, Offset.Zero, ins.map { PortSpec.input(it.first, signal = it.second) } + outs.map { PortSpec.output(it) }, data)

    private fun wire(from: String, fromPort: String, to: String, port: String = "a") = Edge(
        EdgeId("$from.$fromPort->$to.$port"),
        tech.kloos.kompound.graph.model.PortRef(NodeId(from), tech.kloos.kompound.graph.model.PortId(fromPort)),
        tech.kloos.kompound.graph.model.PortRef(NodeId(to), tech.kloos.kompound.graph.model.PortId(port)),
    )

    private fun TestScope.engine(log: MutableList<String>, signalMode: ((GraphNode, PortSpec) -> tech.kloos.kompound.graph.model.SignalMode)? = null) = GraphEngine(
        this,
        mapOf(
            "const" to singleOutputRunner { n, _ -> n.data },
            // routes its input to "then" when the data flag is true, else to "else"; the other port has no signal
            "if" to NodeRunner { ctx ->
                log += "if"
                if (ctx.node.data == true) mapOf("then" to ctx.inputs["a"], "else" to NoSignal) else mapOf("then" to NoSignal, "else" to ctx.inputs["a"])
            },
            "echo" to singleOutputRunner { n, i -> log += "echo ${n.id} ${i["a"]}"; i["a"] },
            "emitNothing" to NodeRunner { ctx -> ctx.emit("out", NoSignal); emptyMap() },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
        signalMode = signalMode ?: { _, port -> port.signal },
    )

    private fun branching(flag: Boolean) = Graph.of(
        listOf(
            node("v", "const", 42),
            node("if", "if", flag, ins = listOf("a" to sm), outs = listOf("then", "else")),
            node("yes", "echo", ins = listOf("a" to sm)),
            node("yes2", "echo", ins = listOf("a" to sm)),
            node("no", "echo", ins = listOf("a" to sm)),
        ),
        listOf(wire("v", "out", "if"), wire("if", "then", "yes"), wire("yes", "out", "yes2"), wire("if", "else", "no")),
    )

    @Test
    fun theBranchNotTakenIsSkippedWithoutRunningItsNodesAndTheSkipSpreads() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        e.update(branching(flag = true))
        advanceUntilIdle()
        assertEquals(listOf("if", "echo yes 42", "echo yes2 42"), log, "only the taken branch ran")
        assertEquals(NodeRun.Skipped, e.runOf(NodeId("no")))
        assertEquals(42, e.output(NodeId("yes2"), "out"))
        e.update(branching(flag = false))
        advanceUntilIdle()
        assertEquals(NodeRun.Skipped, e.runOf(NodeId("yes")))
        assertEquals(NodeRun.Skipped, e.runOf(NodeId("yes2")), "skip cascades")
        assertEquals(42, e.output(NodeId("no"), "out"))
        assertTrue(e.executions.last().attemptsOf(NodeId("yes")).isEmpty(), "a skipped node has no attempt")
        assertTrue(e.runs.values.none { it is NodeRun.Running || it is NodeRun.Waiting }, "nothing is left waiting")
    }

    @Test
    fun collectStillRunsWithAnEmptyListButFinalAndLatestSkip() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        val g = Graph.of(
            listOf(
                node("none", "emitNothing"),
                node("collect", "echo", ins = listOf("a" to tech.kloos.kompound.graph.model.SignalMode.Collect)),
                node("final", "echo", ins = listOf("a" to tech.kloos.kompound.graph.model.SignalMode.Final)),
                node("each", "echo", ins = listOf("a" to tech.kloos.kompound.graph.model.SignalMode.Each)),
            ),
            listOf(wire("none", "out", "collect"), wire("none", "out", "final"), wire("none", "out", "each")),
        )
        e.update(g)
        advanceUntilIdle()
        assertEquals(listOf("echo collect []"), log)
        assertEquals(NodeRun.Skipped, e.runOf(NodeId("final")))
        assertEquals(NodeRun.Skipped, e.runOf(NodeId("each")))
        assertTrue(e.runOf(NodeId("none")) is NodeRun.Done)
    }

    @Test
    fun aPortLeftOutStillReadsAsNullAndOnlyNoSignalMeansNothing() = runTest {
        val log = mutableListOf<String>()
        val e = GraphEngine(
            this,
            mapOf("partial" to NodeRunner { mapOf("a" to 1) }, "echo" to singleOutputRunner { n, i -> log += "echo ${n.id} ${i["a"]}"; i["a"] }),
            runDispatcher = StandardTestDispatcher(testScheduler),
        )
        e.update(Graph.of(
            listOf(node("p", "partial", outs = listOf("a", "b")), node("fromA", "echo", ins = listOf("a" to sm)), node("fromB", "echo", ins = listOf("a" to sm))),
            listOf(wire("p", "a", "fromA"), wire("p", "b", "fromB")),
        ))
        advanceUntilIdle()
        assertEquals(setOf("echo fromA 1", "echo fromB null"), log.toSet())
    }

    @Test
    fun theSignalModeCanBeResolvedAtRunTimeSoSavedPortsFollowTheNewBehaviour() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log, signalMode = { n, port -> if (n.kind == "echo") tech.kloos.kompound.graph.model.SignalMode.Each else port.signal })
        val ticker = GraphNode(NodeId("t"), "ticker", Offset.Zero, listOf(PortSpec.output("out")), 3)
        val consumer = node("c", "echo", ins = listOf("a" to sm))   // saved as Latest
        val withTicker = GraphEngine(
            this,
            mapOf("ticker" to NodeRunner { ctx -> repeat(3) { i -> delay(10); ctx.emit("out", i) }; emptyMap() }, "echo" to singleOutputRunner { n, i -> log += "echo ${i["a"]}"; delay(100); i["a"] }),
            runDispatcher = StandardTestDispatcher(testScheduler),
            signalMode = { n, port -> if (n.kind == "echo") tech.kloos.kompound.graph.model.SignalMode.Each else port.signal },
        )
        withTicker.update(Graph.of(listOf(ticker, consumer), listOf(wire("t", "out", "c"))))
        advanceUntilIdle()
        assertEquals(listOf("echo 0", "echo 1", "echo 2"), log, "Each processed every value although the port says Latest")
        assertTrue(e.runs.isEmpty())
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
class GraphEngineAnyInputTest {
    private fun node(id: String, kind: String, data: Any? = null, ins: List<Pair<String, tech.kloos.kompound.graph.model.SignalMode>> = emptyList(), outs: List<String> = listOf("out")) =
        GraphNode(NodeId(id), kind, Offset.Zero, ins.map { PortSpec.input(it.first, signal = it.second) } + outs.map { PortSpec.output(it) }, data)

    private fun wire(from: String, fromPort: String, to: String, port: String) = Edge(
        EdgeId("$from.$fromPort->$to.$port"),
        tech.kloos.kompound.graph.model.PortRef(NodeId(from), tech.kloos.kompound.graph.model.PortId(fromPort)),
        tech.kloos.kompound.graph.model.PortRef(NodeId(to), tech.kloos.kompound.graph.model.PortId(port)),
    )

    private val any = tech.kloos.kompound.graph.model.SignalMode.Any
    private val latest = tech.kloos.kompound.graph.model.SignalMode.Latest

    private fun TestScope.engine(log: MutableList<String>) = GraphEngine(
        this,
        mapOf(
            "const" to singleOutputRunner { n, _ -> n.data },
            "if" to NodeRunner { ctx -> if (ctx.node.data == true) mapOf("then" to ctx.inputs["a"], "else" to NoSignal) else mapOf("then" to NoSignal, "else" to ctx.inputs["a"]) },
            "silent" to NodeRunner { mapOf("then" to NoSignal, "else" to NoSignal) },
            "slow" to singleOutputRunner { _, i -> delay(200); "slow:" + i["a"] },
            "fast" to singleOutputRunner { _, i -> "fast:" + i["a"] },
            "merge" to singleOutputRunner { n, i -> log += "merge ${i["x"]} ${i["y"]}"; i["x"] ?: i["y"] },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun mergeGraph(flag: Boolean) = Graph.of(
        listOf(
            node("v", "const", 7),
            node("if", "if", flag, ins = listOf("a" to latest), outs = listOf("then", "else")),
            node("t", "slow", ins = listOf("a" to latest)),
            node("e", "fast", ins = listOf("a" to latest)),
            node("m", "merge", ins = listOf("x" to any, "y" to any)),
        ),
        listOf(wire("v", "out", "if", "a"), wire("if", "then", "t", "a"), wire("if", "else", "e", "a"), wire("t", "out", "m", "x"), wire("e", "out", "m", "y")),
    )

    @Test
    fun anyInputsLetAMergeTakeWhicheverBranchHasAValueEvenWhenTheOtherWasSkipped() = runTest {
        for (flag in listOf(true, false)) {
            val log = mutableListOf<String>()
            val e = engine(log)
            e.update(mergeGraph(flag))
            advanceUntilIdle()
            val expected = if (flag) "slow:7" else "fast:7"
            assertEquals(expected, e.output(NodeId("m"), "out"), "flag=$flag")
            assertEquals(1, log.size, "ran once, after both branches settled: $log")
            assertEquals(if (flag) "merge slow:7 null" else "merge null fast:7", log.single())
            assertTrue(e.runOf(NodeId(if (flag) "e" else "t")) is NodeRun.Skipped)
        }
    }

    @Test
    fun anyWaitsForAnInputThatIsStillComingAndSkipsWhenNeitherEverGetsAValue() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        // both branches fed by a node that gives no signal on both ports
        val g = Graph.of(
            listOf(
                node("none", "silent", outs = listOf("then", "else")),
                node("m", "merge", ins = listOf("x" to any, "y" to any)),
            ),
            listOf(wire("none", "then", "m", "x"), wire("none", "else", "m", "y")),
        )
        e.update(g)
        advanceUntilIdle()
        assertTrue(log.isEmpty())
        assertEquals(NodeRun.Skipped, e.runOf(NodeId("m")), "no value on any optional input: nothing to merge")
        // slow branch: the merge does not run early with two nulls
        val log2 = mutableListOf<String>()
        val e2 = engine(log2)
        e2.update(mergeGraph(true))
        advanceTimeBy(100)
        assertTrue(log2.isEmpty(), "the taken branch is still working")
    }

    @Test
    fun anOptionalInputNextToRequiredOnesGetsNullWhenItHasNoSignal() = runTest {
        val log = mutableListOf<String>()
        val e = engine(log)
        val g = Graph.of(
            listOf(
                node("v", "const", 5),
                node("if", "if", true, ins = listOf("a" to latest), outs = listOf("then", "else")),
                node("m", "merge", ins = listOf("x" to latest, "y" to any)),
            ),
            listOf(wire("v", "out", "if", "a"), wire("v", "out", "m", "x"), wire("if", "else", "m", "y")),
        )
        e.update(g)
        advanceUntilIdle()
        assertEquals(listOf("merge 5 null"), log)
    }

    @Test
    fun aDataChangeThatIsNotRelevantKeepsTheResults() = runTest {
        var runs = 0
        val e = GraphEngine(
            this,
            mapOf("count" to singleOutputRunner { _, _ -> ++runs }),
            runDispatcher = StandardTestDispatcher(testScheduler),
            isRelevantChange = { old, new -> (old.data as Pair<*, *>).first != (new.data as Pair<*, *>).first },
        )
        val g = Graph.of(listOf(node("n", "count", "a" to "note1")), emptyList())
        e.update(g)
        advanceUntilIdle()
        assertEquals(1, runs)
        e.update(g.withNode(g.node(NodeId("n"))!!.copy(data = "a" to "note2")))
        advanceUntilIdle()
        assertEquals(1, runs, "only the second part (a note) changed")
        assertTrue(e.runOf(NodeId("n")) is NodeRun.Done)
        e.update(g.withNode(g.node(NodeId("n"))!!.copy(data = "b" to "note2")))
        advanceUntilIdle()
        assertEquals(2, runs, "the relevant part changed")
    }
}
