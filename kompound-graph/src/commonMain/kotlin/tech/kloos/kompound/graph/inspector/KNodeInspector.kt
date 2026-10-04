package tech.kloos.kompound.graph.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dropdown.KDropdown
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.PortDirection
import tech.kloos.kompound.graph.model.PortId
import tech.kloos.kompound.graph.runtime.Execution
import tech.kloos.kompound.graph.runtime.GraphEngine
import tech.kloos.kompound.graph.runtime.LogLevel
import tech.kloos.kompound.graph.runtime.NodeAttempt
import tech.kloos.kompound.graph.runtime.NodeTestRun
import tech.kloos.kompound.graph.runtime.TraceStatus
import tech.kloos.kompound.graph.runtime.pin
import tech.kloos.kompound.graph.runtime.unpin
import tech.kloos.kompound.graph.serialization.GraphJsonException
import tech.kloos.kompound.graph.serialization.JsonValue
import tech.kloos.kompound.graph.serialization.ValueJson
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextArea

private const val AllPorts = "All"

/** The words and defaults of [KNodeInspector]; replace them to translate it. */
public class KNodeInspectorLabels(
    public val input: String = "Input",
    public val output: String = "Output",
    public val parameters: String = "Parameters",
    public val logs: String = "Logs",
    public val testStep: String = "Test step",
    public val editInput: String = "Edit input",
    public val useRunData: String = "Use run data",
    public val pin: String = "Pin output",
    public val unpin: String = "Unpin",
    public val rerun: String = "Run from here",
    public val pinned: String = "Pinned data",
    public val noData: String = "No data yet",
    public val notCaptured: String = "Values were not captured for this run",
    public val noLogs: String = "No log lines",
    public val noParameters: String = "This node has no parameters",
    public val invalidJson: String = "Not valid JSON",
)

/**
 * Looks at one node the way n8n's node view does: what came in on the left, the node's own settings in the middle ([parameters]), what
 * went out on the right, with Schema, Table and JSON views, the node's logs and error, and buttons to test the node alone with edited
 * input, pin its output, or run from it. Wide windows show three columns, narrow ones switch between Input, Parameters and Output.
 *
 * The data comes from the engine's trace: the newest attempt of [node] (or its attempt inside [execution], to look at an older run).
 * A node that has not run shows what its upstream nodes currently produce as input, and its pin (if any) as output. Test runs started
 * from here show up in the trace and are shown right away.
 *
 * @param engine The engine whose trace is shown; also runs the tests.
 * @param node The node to inspect (take it from the current graph so a pin shows up).
 * @param state The editor state: when given, Pin and Unpin edit the graph (one undo step each).
 * @param execution A recorded run to show instead of the newest one.
 * @param parameters The middle column: the node's settings editor, for example the same content as in the canvas node.
 * @param valueJson How values are read back from the input editor and shown; pass one with your codecs for custom types.
 * @param parseInput Turns the text typed in the input editor into the value for a port; the default reads JSON (numbers become
 * `Double`, use `{"$int": 5}` or your own parser for other types).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KNodeInspector(
    engine: GraphEngine,
    node: GraphNode,
    modifier: Modifier = Modifier,
    state: KGraphState? = null,
    execution: Execution? = null,
    parameters: (@Composable () -> Unit)? = null,
    valueJson: ValueJson = ValueJson(),
    labels: KNodeInspectorLabels = KNodeInspectorLabels(),
    parseInput: (port: PortId, text: String) -> Any? = { _, text -> valueJson.decode(JsonValue.parse(text)) },
) {
    var test by remember(node.id) { mutableStateOf<NodeTestRun?>(null) }
    val edits = remember(node.id) { mutableStateMapOf<PortId, String>() }
    var editing by remember(node.id) { mutableStateOf(false) }
    var editError by remember(node.id) { mutableStateOf<String?>(null) }

    val attempt: NodeAttempt? = test?.attempt ?: if (execution != null) execution.latest(node.id) else engine.lastAttempt(node.id)
    val live = execution == null && test == null
    val inputPorts = node.ports.filter { it.direction == PortDirection.Input }
    val outputPorts = node.ports.filter { it.direction == PortDirection.Output }

    // What the node received: its attempt's inputs, otherwise what upstream produces right now.
    val inputs: Map<PortId, Any?> = attempt?.inputs ?: if (live) engine.currentInputs(node.id) else emptyMap()
    val outputs: Map<PortId, Any?>? = when {
        test != null -> test?.outputs ?: attempt?.outputs
        attempt?.outputs != null -> attempt.outputs
        live -> node.pin ?: engine.outputsOf(node.id)
        else -> null
    }
    val showingPin = test == null && attempt?.outputs == null && live && node.pin != null
    val realOutputs: Map<PortId, Any?>? = test?.outputs ?: engine.outputsOf(node.id)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        var column by remember { mutableStateOf(0) }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header(node, attempt, labels, showingPin) {
                KButton(
                    {
                        editError = null
                        val merged = LinkedHashMap<PortId, Any?>(engine.currentInputs(node.id)).also { it.putAll(inputs) }
                        try {
                            for ((port, text) in edits) merged[port] = parseInput(port, text)
                            test = engine.testNode(node.id, merged.mapKeys { it.key.value })
                        } catch (e: GraphJsonException) {
                            editError = "${labels.invalidJson}: ${e.message}"
                        }
                    },
                    variant = KButtonVariant.Tonal,
                ) { KText(labels.testStep) }
                if (state != null) {
                    if (node.pin != null) KButton({ state.unpin(node.id) }, variant = KButtonVariant.Outlined) { KText(labels.unpin) }
                    else KButton({ realOutputs?.let { state.pin(node.id, it) } }, variant = KButtonVariant.Outlined, enabled = realOutputs != null) { KText(labels.pin) }
                }
                KButton({ test = null; engine.rerun(node.id) }, variant = KButtonVariant.Outlined) { KText(labels.rerun) }
            }
            if (!wide) {
                KSegmentedControl(listOf(labels.input, labels.parameters, labels.output), column, { column = it }, Modifier.fillMaxWidth())
            }
            val inputPane: @Composable (Modifier) -> Unit = { m ->
                DataPane(
                    title = labels.input, values = inputs, ports = inputPorts.map { it.id }, valueJson = valueJson, labels = labels, modifier = m,
                    captured = attempt?.valuesCaptured ?: true,
                    extra = {
                        KButton({ editing = !editing; if (editing) inputPorts.forEach { p -> edits.getOrPut(p.id) { displayValue(inputs[p.id], valueJson).toJson(pretty = true) } } else edits.clear() }, variant = KButtonVariant.Text) {
                            KText(if (editing) labels.useRunData else labels.editInput)
                        }
                    },
                    editor = if (editing) edits else null,
                    editError = editError,
                    onEdit = { port, text -> edits[port] = text; editError = null },
                )
            }
            val outputPane: @Composable (Modifier) -> Unit = { m ->
                OutputPane(outputs, outputPorts.map { it.id }, attempt, valueJson, labels, showingPin, m)
            }
            val parameterPane: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(8.dp)) {
                    if (parameters != null) parameters() else KText(labels.noParameters)
                }
            }
            val paneModifier = Modifier.fillMaxHeight()
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inputPane(paneModifier.weight(1f))
                    parameterPane(paneModifier.weight(1f))
                    outputPane(paneModifier.weight(1f))
                }
            } else {
                when (column) {
                    0 -> inputPane(Modifier.fillMaxSize())
                    1 -> parameterPane(Modifier.fillMaxSize())
                    else -> outputPane(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(node: GraphNode, attempt: NodeAttempt?, labels: KNodeInspectorLabels, pinned: Boolean, actions: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KText("${node.kind} · ${node.id}")
            when {
                pinned -> KText(labels.pinned)
                attempt != null -> {
                    KText(attempt.status.name)
                    attempt.durationMillis?.let { KText(durationText(it)) }
                    if (attempt.number > 1) KText("attempt ${attempt.number}")
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
private fun DataPane(
    title: String,
    values: Map<PortId, Any?>,
    ports: List<PortId>,
    valueJson: ValueJson,
    labels: KNodeInspectorLabels,
    modifier: Modifier,
    captured: Boolean,
    extra: (@Composable () -> Unit)? = null,
    editor: Map<PortId, String>? = null,
    editError: String? = null,
    onEdit: (PortId, String) -> Unit = { _, _ -> },
) {
    var mode by remember { mutableStateOf(ValueViewMode.Json) }
    var portIndex by remember { mutableStateOf(0) }
    val options = if (ports.size > 1) listOf(AllPorts) + ports.map { it.value } else ports.map { it.value }
    val chosen = options.getOrNull(portIndex)
    PaneFrame(title, modifier) {
        KSegmentedControl(ValueViewMode.entries.map { it.label }, mode.ordinal, { mode = ValueViewMode.entries[it] }, Modifier.fillMaxWidth())
        extra?.invoke()
        if (options.size > 1) KDropdown(options, chosen, { portIndex = options.indexOf(it) }, Modifier.fillMaxWidth())
        val shown: Any? = if (chosen == null || chosen == AllPorts) values.mapKeys { it.key.value } else values[PortId(chosen)]
        when {
            editor != null -> {
                val port = ports.getOrNull((portIndex - if (ports.size > 1) 1 else 0).coerceAtLeast(0))
                if (port != null) {
                    KTextArea(
                        editor[port] ?: "", { onEdit(port, it) }, Modifier.fillMaxWidth(),
                        label = port.value, minLines = 6, maxLines = 16, isError = editError != null, supportingText = editError,
                    )
                }
            }
            !captured -> KText(labels.notCaptured)
            values.isEmpty() -> KText(labels.noData)
            else -> KValueView(shown, Modifier.weight(1f).fillMaxWidth(), mode, valueJson)
        }
    }
}

@Composable
private fun OutputPane(
    outputs: Map<PortId, Any?>?,
    ports: List<PortId>,
    attempt: NodeAttempt?,
    valueJson: ValueJson,
    labels: KNodeInspectorLabels,
    showingPin: Boolean,
    modifier: Modifier,
) {
    var tab by remember { mutableStateOf(0) }
    PaneFrame(labels.output, modifier) {
        KSegmentedControl(listOf(labels.output, labels.logs), tab, { tab = it }, Modifier.fillMaxWidth())
        if (tab == 0) {
            val error = attempt?.error
            when {
                error != null -> ErrorBlock(error)
                outputs == null -> KText(labels.noData)
                attempt?.valuesCaptured == false && !showingPin -> KText(labels.notCaptured)
                else -> {
                    var mode by remember { mutableStateOf(ValueViewMode.Json) }
                    KSegmentedControl(ValueViewMode.entries.map { it.label }, mode.ordinal, { mode = ValueViewMode.entries[it] }, Modifier.fillMaxWidth())
                    val single = if (ports.size == 1) outputs[ports.first()] else null
                    KValueView(if (ports.size == 1) single else outputs.mapKeys { it.key.value }, Modifier.weight(1f).fillMaxWidth(), mode, valueJson)
                }
            }
        } else {
            LogsView(attempt, labels, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun ErrorBlock(error: Throwable) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KText(error::class.simpleName ?: "Error")
        KText(error.message ?: "No message")
    }
}

@Composable
private fun LogsView(attempt: NodeAttempt?, labels: KNodeInspectorLabels, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val progress = attempt?.progress
        val message = attempt?.progressMessage
        if (progress != null || message != null) KText("progress ${progress?.let { "${(it * 100).toInt()}%" } ?: ""} ${message ?: ""}".trim())
        val logs = attempt?.logs.orEmpty()
        if (logs.isEmpty() && progress == null && message == null) KText(labels.noLogs)
        for (line in logs) {
            val level = if (line.level == LogLevel.Info) "" else "${line.level.name.uppercase()} "
            KText("+${line.at - (attempt?.startedAt ?: line.at)} ms  $level${line.message}")
        }
        attempt?.takeIf { it.emissions.isNotEmpty() }?.let { a ->
            KText("")
            KText("${a.emissions.size} emitted")
            for (e in a.emissions) KText("+${e.at - a.startedAt} ms  ${e.port}: ${previewValue(e.value)}")
        }
    }
}

@Composable
private fun PaneFrame(title: String, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KText(title)
        content()
    }
}
