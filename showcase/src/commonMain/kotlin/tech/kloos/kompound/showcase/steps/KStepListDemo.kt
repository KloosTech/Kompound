package tech.kloos.kompound.showcase.steps

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.steps.KStep
import tech.kloos.kompound.steps.KStepList
import tech.kloos.kompound.steps.KStepState

private const val Usage_steps_list = """import tech.kloos.kompound.steps.KStep
import tech.kloos.kompound.steps.KStepList
import tech.kloos.kompound.steps.KStepState

// Describe where each step is; the list animates the changes.
KStepList(
    steps = listOf(
        KStep("Download update", KStepState.Done, trailing = "0:12"),
        KStep("Install", KStepState.InProgress(progress = 0.4f), trailing = "0:30 left"),
        KStep("Verify", KStepState.InProgress(progress = null)),    // unknown amount: indeterminate
        KStep("Restart"),                                          // waiting
    ),
)"""

@KompoundDemo(
    id = "steps.list",
    title = "KStepList",
    description = "Vertical list of the steps of a longer task, each with a progress line, status icon and trailing note.",
    category = KompoundCategory.Feedback,
    tags = ["steps", "progress", "stepper", "update", "wizard", "status"],
    since = "0.1.0",
    usage = Usage_steps_list,
)
@Composable
fun DemoScope.KStepListDemo() {
    val first = choiceControl("Step 1", listOf("Done", "In progress", "Waiting"))
    val second = choiceControl("Step 2", listOf("In progress", "Done", "Waiting"))
    val progress = floatControl("Step 2 progress", 0f..1f, 0.4f)
    val unknown = boolControl("Step 3 indeterminate", false)
    fun state(name: String, p: Float?) = when (name) {
        "Done" -> KStepState.Done
        "In progress" -> KStepState.InProgress(p)
        else -> KStepState.Waiting
    }
    KStepList(
        steps = listOf(
            KStep("Download update", state(first, 1f), "0:12"),
            KStep("Install", state(second, progress), if (second == "In progress") "0:30 left" else null),
            KStep("Verify", if (unknown) KStepState.InProgress(null) else KStepState.Waiting),
            KStep("Restart"),
        ),
        modifier = Modifier.padding(16.dp),
    )
}
