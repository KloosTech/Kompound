package tech.kloos.kompound.showcase.snackbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.snackbar.KSnackbarDuration
import tech.kloos.kompound.snackbar.KSnackbarHost
import tech.kloos.kompound.snackbar.KSnackbarHostState
import tech.kloos.kompound.snackbar.KSnackbarTone
import tech.kloos.kompound.text.KText

@KompoundDemo(
    id = "snackbar.basic",
    title = "KSnackbar",
    description = "Short message with an optional action, queued by a host state, in five tones and three durations.",
    category = KompoundCategory.Feedback,
    tags = ["snackbar", "toast", "message", "notification", "flash"],
    since = "0.1.0",
)
@Composable
fun DemoScope.KSnackbarDemo() {
    val message = textControl("Message", "Message sent")
    val tone = choiceControl("Tone", KSnackbarTone.entries)
    val action = boolControl("Action button", true)
    val duration = choiceControl("Duration", KSnackbarDuration.entries, initial = KSnackbarDuration.Short)
    val host = remember { KSnackbarHostState() }
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf("Nothing shown yet") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KButton(onClick = {
            scope.launch { last = "Result: " + host.showSnackbar(message, if (action) "Undo" else null, tone, duration) }
        }) { KText("Show snackbar") }
        KText(last)
        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.BottomCenter) { KSnackbarHost(host) }
    }
}
