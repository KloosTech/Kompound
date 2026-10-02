package tech.kloos.kompound.showcase.state

import androidx.compose.runtime.Composable
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.state.KEmptyState
import tech.kloos.kompound.state.KErrorState
import tech.kloos.kompound.text.KText

private const val Usage_state_empty = """import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.state.KEmptyState
import tech.kloos.kompound.text.KText

KEmptyState(
    title = "No messages yet",
    description = "New conversations show up here.",
    action = { KButton(onClick = { compose() }) { KText("Write one") } },
)"""

@KompoundDemo(
    id = "state.empty",
    title = "KEmptyState",
    description = "Placeholder for a screen or list with nothing to show: illustration, title, description and an action.",
    category = KompoundCategory.Feedback,
    tags = ["empty", "state", "placeholder", "no results", "zero"],
    since = "0.1.0",
    usage = Usage_state_empty,
)
@Composable
fun DemoScope.KEmptyStateDemo() {
    val title = textControl("Title", "No messages yet")
    val description = boolControl("Description", true)
    val illustration = boolControl("Illustration", true)
    val action = boolControl("Action", true)
    KEmptyState(
        title = title,
        description = if (description) "When someone writes to you, the conversation shows up here." else null,
        illustration = if (illustration) ({ KIcon(DemoIcons.Star, null) }) else null,
        action = if (action) ({ KButton(onClick = {}) { KText("Write a message") } }) else null,
    )
}

private const val Usage_state_error = """import tech.kloos.kompound.state.KErrorState

KErrorState(
    title = "Could not load your files",
    description = "Check your connection and try again.",
    onRetry = { reload() },
)"""

@KompoundDemo(
    id = "state.error",
    title = "KErrorState",
    description = "Placeholder for a failure with an error icon, message and retry button; announced to screen readers.",
    category = KompoundCategory.Feedback,
    tags = ["error", "state", "retry", "failure", "offline"],
    since = "0.1.0",
    usage = Usage_state_error,
)
@Composable
fun DemoScope.KErrorStateDemo() {
    val retry = boolControl("Retry button", true)
    val description = boolControl("Description", true)
    KErrorState(
        title = "Could not load messages",
        description = if (description) "Check your connection and try again." else null,
        onRetry = if (retry) ({}) else null,
    )
}
