package tech.kloos.kompound.showcase.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KProgressButton
import tech.kloos.kompound.demo.DemoScope

private const val Usage_button_progress = """import tech.kloos.kompound.buttons.KProgressButton

var upload by remember { mutableStateOf<Float?>(null) }   // null = no upload running

KProgressButton(
    text = "Upload report",
    progress = upload,
    onClick = { startUpload { fraction -> upload = fraction } },   // report 0f..1f, then set null when done
    modifier = Modifier.fillMaxWidth(),
    progressText = { percent -> "Uploading " + percent + " %" },   // optional, e.g. to localise
)"""

@KompoundDemo(
    id = "button.progress",
    title = "KProgressButton",
    description = "A button that doubles as its own progress bar: a fill sweeps behind a percentage label while a task runs.",
    category = KompoundCategory.Buttons,
    tags = ["button", "progress", "upload", "download", "loading", "fill"],
    since = "0.1.0",
    usage = Usage_button_progress,
)
@Composable
fun DemoScope.KProgressButtonDemo() {
    val idle = boolControl("Idle (no progress)", false)
    val progress = floatControl("Progress", 0f..1f, 0.4f)
    val enabled = boolControl("Enabled", true)
    val blockClicks = boolControl("Ignore clicks while running", true)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KProgressButton("Upload report", if (idle) null else progress, {}, Modifier.fillMaxWidth(), enabled = enabled, disableWhileInProgress = blockClicks)
        KProgressButton("Short", if (idle) null else progress, {}, enabled = enabled, disableWhileInProgress = blockClicks)
    }
}
