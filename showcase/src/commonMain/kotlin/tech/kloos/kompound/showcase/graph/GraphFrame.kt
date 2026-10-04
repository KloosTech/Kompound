package tech.kloos.kompound.showcase.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.graph.serialization.GraphJson
import tech.kloos.kompound.graph.serialization.GraphJsonException
import tech.kloos.kompound.graph.serialization.loadJson
import tech.kloos.kompound.graph.serialization.toJson
import tech.kloos.kompound.textfield.KTextArea
import tech.kloos.kompound.text.KText

/**
 * Shows a graph inline with a "Full screen" button; the button moves the same graph (same [state], so nothing is lost) into a
 * full-window dialog and fits the view. [graph] draws the canvas with the modifier it is given.
 */
@Composable
internal fun GraphFrame(state: KGraphState, inlineHeight: Int = 540, json: GraphJson = GraphJson(), graph: @Composable (Modifier) -> Unit) {
    var full by remember { mutableStateOf(false) }
    var jsonOpen by remember { mutableStateOf(false) }
    LaunchedEffect(full) {
        delay(120)
        state.fitView()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!full) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KButton({ full = true }, variant = KButtonVariant.Tonal) { KText("Full screen") }
                KButton({ jsonOpen = true }, variant = KButtonVariant.Outlined) { KText("JSON") }
            }
            graph(Modifier.fillMaxWidth().height(inlineHeight.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)))
        }
    }
    if (jsonOpen) JsonDialog(state, json) { jsonOpen = false }
    if (full) {
        Dialog({ full = false }, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                graph(Modifier.fillMaxSize())
                KButton({ full = false }, Modifier.align(Alignment.TopStart).padding(12.dp), variant = KButtonVariant.Tonal) { KText("Exit full screen") }
            }
        }
    }
}

@Composable
private fun JsonDialog(state: KGraphState, json: GraphJson, onClose: () -> Unit) {
    var text by remember { mutableStateOf(state.toJson(json)) }
    var error by remember { mutableStateOf<String?>(null) }
    KDialog(
        onDismissRequest = onClose,
        title = "Graph as JSON",
        actions = {
            KButton({ text = state.toJson(json); error = null }, variant = KButtonVariant.Text) { KText("Reset") }
            KButton({
                try {
                    state.loadJson(text, json)
                    onClose()
                } catch (e: GraphJsonException) {
                    error = e.message
                }
            }) { KText("Load") }
        },
    ) {
        KTextArea(
            text, { text = it; error = null }, Modifier.fillMaxWidth(),
            label = "JSON", minLines = 8, maxLines = 14,
            isError = error != null, supportingText = error ?: "Edit it and press Load, or copy it to save the graph.",
        )
    }
}
