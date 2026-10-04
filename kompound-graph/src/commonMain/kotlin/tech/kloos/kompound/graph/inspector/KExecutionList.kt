package tech.kloos.kompound.graph.inspector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.graph.runtime.Execution
import tech.kloos.kompound.graph.runtime.GraphEngine
import tech.kloos.kompound.graph.runtime.TraceStatus
import tech.kloos.kompound.graph.runtime.TraceTrigger
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

/** "1.2 s" or "340 ms". */
internal fun durationText(millis: Long): String = if (millis >= 1000) "${millis / 100 / 10.0} s" else "$millis ms"

@Composable
internal fun statusColor(status: TraceStatus): Color = when (status) {
    TraceStatus.Succeeded -> KompoundTheme.tokens.colors.success
    TraceStatus.Failed -> MaterialTheme.colorScheme.error
    TraceStatus.Running -> MaterialTheme.colorScheme.primary
    TraceStatus.Cancelled -> MaterialTheme.colorScheme.outline
}

/**
 * The executions the engine recorded, newest first, under a "Live" row. Pick one to look at it: pass the selection to
 * `engine.nodeStatus(node, execution)` and `engine.edgeLabel(edge, execution)` for the canvas and to [KNodeInspector] for the data.
 * `null` is "Live" (the current state of the engine).
 *
 * @param selected The execution being looked at, or `null` for live.
 * @param onSelect Called with the picked execution (`null` for live).
 */
@Composable
public fun KExecutionList(
    engine: GraphEngine,
    selected: Execution?,
    onSelect: (Execution?) -> Unit,
    modifier: Modifier = Modifier,
    liveLabel: String = "Live",
) {
    val executions = engine.executions.asReversed()
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "live") {
            ExecutionRow(liveLabel, if (engine.currentRun != null) "running" else "current state", statusDot = if (engine.currentRun != null) TraceStatus.Running else null, isSelected = selected == null) { onSelect(null) }
        }
        items(executions, key = { it.id }) { execution ->
            val took = execution.finishedAt?.let { durationText(it - execution.startedAt) } ?: "running"
            val trigger = when (execution.trigger) {
                TraceTrigger.Auto -> "edit"
                TraceTrigger.Manual -> "run"
                TraceTrigger.Rerun -> "re-run"
                TraceTrigger.Test -> "test"
            }
            ExecutionRow("#${execution.id} $trigger", "$took, ${execution.attempts.map { it.nodeId }.toSet().size} nodes", execution.status, selected === execution) { onSelect(execution) }
        }
    }
}

@Composable
private fun ExecutionRow(title: String, detail: String, statusDot: TraceStatus?, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dot = statusDot?.let { statusColor(it) } ?: scheme.outline
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) scheme.secondaryContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected; contentDescription = "$title, $detail" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(10.dp)) { drawCircle(dot) }
        KText(title, Modifier.weight(1f), maxLines = 1)
        KText(detail, maxLines = 1)
    }
}
