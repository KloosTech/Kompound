package tech.kloos.kompound.graph.runtime

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TriggerTest {
    private fun trigger(id: String = "t", data: Any? = null, pin: Map<PortId, Any?>? = null) =
        GraphNode(NodeId(id), "hook", Offset.Zero, listOf(PortSpec.output("out")), data, pin = pin)

    private fun node(id: String, kind: String, vararg inputs: String) =
        GraphNode(NodeId(id), kind, Offset.Zero, inputs.map { PortSpec.input(it) } + PortSpec.output("out"))

    private fun wire(from: String, to: String, port: String = "a") =
        Edge(EdgeId("$from->$to.$port"), PortRef(NodeId(from), PortId("out")), PortRef(NodeId(to), PortId(port)))

    private val engines = mutableListOf<GraphEngine>()

    /** An armed trigger never ends on its own: stop every engine at the end so runTest does not wait for the listeners. */
    private fun runT(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { engines.forEach { it.stop() } }
    }

    private class Hook { var ctx: TriggerContext? = null; var armed = 0 }

    private fun TestScope.engine(
        hook: Hook,
        log: MutableList<String> = mutableListOf(),
        policy: EventPolicy = EventPolicy.Default,
        beforeRun: ((GraphNode, TraceTrigger) -> Boolean)? = null,
    ): GraphEngine = GraphEngine(
        this,
        mapOf(
            "double" to singleOutputRunner { n, i -> log += "double ${n.id}"; delay(100); (i["a"] as Int) * 2 },
            "const" to singleOutputRunner { n, _ -> log += "const ${n.id}"; n.data },
            "add" to singleOutputRunner { n, i -> log += "add ${n.id}"; (i["a"] as Int) + (i["b"] as Int) },
            "unrelated" to singleOutputRunner { n, _ -> log += "unrelated ${n.id}"; 1 },
        ),
        runDispatcher = StandardTestDispatcher(testScheduler),
        triggers = mapOf("hook" to TriggerRunner { ctx -> hook.ctx = ctx; hook.armed++; awaitCancellation() }),
        eventPolicy = { policy },
        beforeRun = beforeRun,
    ).also { engines += it }

    @Test
    fun aTriggerListensAndEachEventRunsWhatIsDownstreamOnce() = runT {
        val hook = Hook(); val log = mutableListOf<String>()
        val e = engine(hook, log)
        e.update(Graph.of(listOf(trigger(), node("d", "double", "a"), node("u", "unrelated")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        assertEquals(NodeRun.Listening, e.runOf(NodeId("t")))
        assertTrue(e.isListening)
        assertFalse(e.isBusy, "listening is not busy")
        assertEquals(listOf("unrelated u"), log, "nothing downstream runs before an event")
        e.awaitIdle()   // returns although a trigger is armed

        hook.ctx!!.fire(mapOf("out" to 21))
        advanceUntilIdle()
        assertEquals(42, e.output(NodeId("d"), "out"))
        assertEquals(listOf("unrelated u", "double d"), log, "the unrelated node did not run again")
        val event = e.executions.last()
        assertEquals(TraceTrigger.Event, event.trigger)
        assertEquals(TraceStatus.Succeeded, event.status)
        assertEquals(21, event.latest(NodeId("t"))!!.outputs!![PortId("out")], "the trigger's attempt carries the event")
        assertEquals(42, event.latest(NodeId("d"))!!.outputs!![PortId("out")])
    }

    @Test
    fun overlappingEventsAreIsolatedFromEachOther() = runT {
        val hook = Hook()
        val e = engine(hook, policy = EventPolicy(EventPolicy.Mode.Queue, maxConcurrent = 2))
        e.update(Graph.of(listOf(trigger(), node("d", "double", "a")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        hook.ctx!!.fire(mapOf("out" to 1))
        hook.ctx!!.fire(mapOf("out" to 10))
        advanceUntilIdle()
        val results = e.executions.filter { it.trigger == TraceTrigger.Event }.map { it.latest(NodeId("d"))!!.outputs!![PortId("out")] }
        assertEquals(listOf(2, 20), results.sortedBy { it as Int })
        assertEquals(2, e.executions.count { it.trigger == TraceTrigger.Event })
    }

    @Test
    fun theQueueRunsEventsInOrderAndDropsTheOldestWhenFull() = runT {
        val hook = Hook()
        val e = engine(hook, policy = EventPolicy(EventPolicy.Mode.Queue, maxConcurrent = 1, maxQueued = 2))
        e.update(Graph.of(listOf(trigger(), node("d", "double", "a")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        for (v in 1..4) hook.ctx!!.fire(mapOf("out" to v))   // 1 runs, 2 and 3 wait, 4 pushes 2 out
        advanceUntilIdle()
        val order = e.executions.filter { it.trigger == TraceTrigger.Event }.map { it.latest(NodeId("t"))!!.outputs!![PortId("out")] }
        assertEquals(listOf<Any?>(1, 3, 4), order)
    }

    @Test
    fun dropIgnoresEventsWhileOneRunsAndLatestReplacesIt() = runT {
        val dropHook = Hook()
        val drop = engine(dropHook, policy = EventPolicy(EventPolicy.Mode.Drop))
        drop.update(Graph.of(listOf(trigger(), node("d", "double", "a")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        dropHook.ctx!!.fire(mapOf("out" to 1))
        dropHook.ctx!!.fire(mapOf("out" to 2))
        advanceUntilIdle()
        assertEquals(1, drop.executions.count { it.trigger == TraceTrigger.Event })

        val latestHook = Hook()
        val latest = engine(latestHook, policy = EventPolicy(EventPolicy.Mode.Latest))
        latest.update(Graph.of(listOf(trigger(), node("d", "double", "a")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        latestHook.ctx!!.fire(mapOf("out" to 1))
        advanceTimeBy(10)
        latestHook.ctx!!.fire(mapOf("out" to 2))
        advanceUntilIdle()
        val events = latest.executions.filter { it.trigger == TraceTrigger.Event }
        assertEquals(listOf(TraceStatus.Cancelled, TraceStatus.Succeeded), events.map { it.status })
        assertEquals(4, latest.output(NodeId("d"), "out"))
    }

    @Test
    fun whatAnEventNeedsFromUpstreamIsNotRunAgain() = runT {
        val hook = Hook(); val log = mutableListOf<String>()
        val e = engine(hook, log)
        e.update(Graph.of(
            listOf(trigger(), node("base", "const"), node("sum", "add", "a", "b")).map { if (it.id.value == "base") it.copy(data = 100) else it },
            listOf(wire("t", "sum", "a"), wire("base", "sum", "b")),
        ))
        advanceUntilIdle()
        assertEquals(listOf("const base"), log)
        hook.ctx!!.fire(mapOf("out" to 5))
        hook.ctx!!.fire(mapOf("out" to 6))
        advanceUntilIdle()
        assertEquals(106, e.output(NodeId("sum"), "out"))
        assertEquals(listOf("const base", "add sum", "add sum"), log, "the constant was used as it was, once")
    }

    @Test
    fun editingTheTriggerRestartsTheListenerButOtherEditsKeepIt() = runT {
        val hook = Hook()
        val e = engine(hook)
        val g = Graph.of(listOf(trigger(data = 1), node("d", "double", "a")), listOf(wire("t", "d")))
        e.update(g)
        advanceUntilIdle()
        assertEquals(1, hook.armed)
        e.update(g.withNode(g.node(NodeId("d"))!!.copy(position = Offset(50f, 50f))))
        advanceUntilIdle()
        assertEquals(1, hook.armed, "moving a node does not re-arm")
        e.update(g.withNode(g.node(NodeId("t"))!!.copy(data = 2)))
        advanceUntilIdle()
        assertEquals(2, hook.armed, "changing the trigger's data re-arms it")
        assertEquals(NodeRun.Listening, e.runOf(NodeId("t")))
    }

    @Test
    fun aPinnedTriggerFiresItsPinOnceInsteadOfListening() = runT {
        val hook = Hook()
        val e = engine(hook)
        e.update(Graph.of(listOf(trigger(pin = mapOf(PortId("out") to 7)), node("d", "double", "a")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        assertEquals(0, hook.armed)
        assertFalse(e.isListening)
        assertEquals(14, e.output(NodeId("d"), "out"))
    }

    @Test
    fun stopCancelsTheListenerAndStartArmsItAgain() = runT {
        val hook = Hook()
        val e = engine(hook)
        e.update(Graph.of(listOf(trigger(), node("d", "double", "a")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        e.stop()
        advanceUntilIdle()
        assertFalse(e.isListening)
        assertEquals(NodeRun.Idle, e.runOf(NodeId("t")))
        e.start()
        advanceUntilIdle()
        assertTrue(e.isListening)
        assertEquals(2, hook.armed)
    }

    @Test
    fun theGateIsAskedForTheTriggerAndForTheNodesOfAnEvent() = runT {
        val hook = Hook()
        val asked = mutableListOf<Pair<String, TraceTrigger>>()
        val e = engine(hook, beforeRun = { n, t -> asked += n.id.value to t; true })
        e.update(Graph.of(listOf(trigger(), node("d", "double", "a")), listOf(wire("t", "d"))))
        advanceUntilIdle()
        hook.ctx!!.fire(mapOf("out" to 1))
        advanceUntilIdle()
        assertTrue("t" to TraceTrigger.Auto in asked, "$asked")
        assertTrue("d" to TraceTrigger.Event in asked, "$asked")
        // a refusing gate keeps the trigger from arming
        val refused = engine(Hook(), beforeRun = { n, _ -> n.id.value != "t" })
        refused.update(Graph.of(listOf(trigger()), emptyList()))
        advanceUntilIdle()
        assertEquals(NodeRun.Declined, refused.runOf(NodeId("t")))
    }

    @Test
    fun aFailingListenerFailsTheTrigger() = runT {
        val e = GraphEngine(this, emptyMap(), runDispatcher = StandardTestDispatcher(testScheduler), triggers = mapOf("hook" to TriggerRunner { error("no socket") }))
        e.update(Graph.of(listOf(trigger()), emptyList()))
        advanceUntilIdle()
        assertTrue(e.runOf(NodeId("t")) is NodeRun.Failed)
    }
}
