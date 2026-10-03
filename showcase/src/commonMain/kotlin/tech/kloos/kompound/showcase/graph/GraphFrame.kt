package tech.kloos.kompound.showcase.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import tech.kloos.kompound.graph.KGraphState
import tech.kloos.kompound.text.KText

/**
 * Shows a graph inline with a "Full screen" button; the button moves the same graph (same [state], so nothing is lost) into a
 * full-window dialog and fits the view. [graph] draws the canvas with the modifier it is given.
 */
@Composable
internal fun GraphFrame(state: KGraphState, inlineHeight: Int = 540, graph: @Composable (Modifier) -> Unit) {
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(full) {
        delay(120)
        state.fitView()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!full) {
            KButton({ full = true }, variant = KButtonVariant.Tonal) { KText("Full screen") }
            graph(Modifier.fillMaxWidth().height(inlineHeight.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)))
        }
    }
    if (full) {
        Dialog({ full = false }, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                graph(Modifier.fillMaxSize())
                KButton({ full = false }, Modifier.align(Alignment.TopStart).padding(12.dp), variant = KButtonVariant.Tonal) { KText("Exit full screen") }
            }
        }
    }
}
