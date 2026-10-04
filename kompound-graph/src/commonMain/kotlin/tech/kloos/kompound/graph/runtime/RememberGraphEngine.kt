package tech.kloos.kompound.graph.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import tech.kloos.kompound.graph.KGraphState

/**
 * A [GraphEngine] that follows the editor: every change of [state]'s graph is handed to [GraphEngine.update], and running stops when the
 * composable leaves the composition (its coroutine scope is cancelled, which cancels every runner). [runners] is read once.
 *
 * @param state The editor state whose graph is run.
 * @param runners Runner per node kind.
 * @param autoRun Whether edits start runs by themselves; with `false` call [GraphEngine.start] (a Run button).
 * @param maxConcurrency How many runners may be active at once.
 * @param runDispatcher Where runners execute.
 */
@Composable
public fun rememberGraphEngine(
    state: KGraphState,
    runners: Map<String, NodeRunner>,
    autoRun: Boolean = true,
    maxConcurrency: Int = 4,
    runDispatcher: CoroutineDispatcher = Dispatchers.Default,
): GraphEngine {
    val scope = rememberCoroutineScope()
    val engine = remember(state, scope) { GraphEngine(scope, runners, autoRun, maxConcurrency, runDispatcher) }
    LaunchedEffect(engine) { snapshotFlow { state.graph }.collect { engine.update(it) } }
    return engine
}
