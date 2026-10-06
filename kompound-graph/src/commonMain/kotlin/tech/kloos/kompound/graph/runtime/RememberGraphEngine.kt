package tech.kloos.kompound.graph.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.GraphResolver
import tech.kloos.kompound.graph.model.LinkedSubgraphs
import tech.kloos.kompound.graph.model.ObservableGraphResolver
import tech.kloos.kompound.graph.model.PortSpec
import tech.kloos.kompound.graph.model.SignalMode

/**
 * A [GraphEngine] that follows the editor: every change of [state]'s graph is handed to [GraphEngine.update], and running stops when the
 * composable leaves the composition (its coroutine scope is cancelled, which cancels every runner). [runners] is read once.
 *
 * @param state The editor state whose graph is run.
 * @param runners Runner per node kind.
 * @param autoRun Whether edits start runs by themselves; with `false` call [GraphEngine.start] (a Run button).
 * @param maxConcurrency How many runners may be active at once.
 * @param runDispatcher Where runners execute.
 * @param trace What is recorded (history size, value capture, redaction).
 * @param beforeRun Gate asked before a runner starts (see [GraphEngine]).
 * @param signalMode Which signal mode an input port uses (see [GraphEngine]).
 * @param links Resolves link nodes ([LinkedSubgraphs]); the engine then runs the expanded graph. A resolver that is a [ObservableGraphResolver] re-expands when a document changes.
 * @param kindConcurrency How many runs of one node kind may be active at once (see [GraphEngine]).
 * @param isRelevantChange Whether a change of a node's data makes it stale (see [GraphEngine]).
 */
@Composable
public fun rememberGraphEngine(
    state: KGraphState,
    runners: Map<String, NodeRunner>,
    autoRun: Boolean = true,
    maxConcurrency: Int = 4,
    runDispatcher: CoroutineDispatcher = Dispatchers.Default,
    trace: TraceOptions = TraceOptions(),
    beforeRun: ((node: GraphNode, trigger: TraceTrigger) -> Boolean)? = null,
    signalMode: (node: GraphNode, port: PortSpec) -> SignalMode = { _, port -> port.signal },
    isRelevantChange: (old: GraphNode, new: GraphNode) -> Boolean = { _, _ -> true },
    kindConcurrency: Map<String, Int> = emptyMap(),
    links: GraphResolver? = null,
): GraphEngine {
    val scope = rememberCoroutineScope()
    val engine = remember(state, scope) { GraphEngine(scope, runners, autoRun, maxConcurrency, runDispatcher, trace, beforeRun = beforeRun, signalMode = signalMode, isRelevantChange = isRelevantChange, kindConcurrency = kindConcurrency) }
    LaunchedEffect(engine, links) {
        snapshotFlow { state.graph to ((links as? ObservableGraphResolver)?.revision ?: 0) }.collect { (graph, _) ->
            engine.update(if (links == null) graph else LinkedSubgraphs.expand(graph, links).graph)
        }
    }
    return engine
}
