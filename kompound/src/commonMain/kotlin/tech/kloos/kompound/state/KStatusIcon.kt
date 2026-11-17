package tech.kloos.kompound.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.progress.KCircularProgress
import tech.kloos.kompound.theme.KompoundTheme

/** Outcome of a task, a tool call or a row of a table; see [KStatusIcon]. */
public enum class KStatus {
    /** Not started yet: an empty ring. */
    Waiting,

    /** Running: a spinner. */
    Running,

    /** Finished well: a check mark in the success colour. */
    Success,

    /** Failed: a cross in the error colour. */
    Error,

    /** Cancelled or skipped: a dash in the quiet colour. */
    Stopped,
}

/**
 * A small mark for a [KStatus]: ring, spinner, check, cross or dash, coloured by meaning and announced to screen readers.
 * Put it in a list row, a step, a tree node or a table cell.
 *
 * @param status What to show.
 * @param modifier Modifier applied to the mark.
 * @param size Edge length.
 * @param contentDescription Accessibility text; the localised word for the status by default, `""` makes the mark decorative.
 */
@Composable
public fun KStatusIcon(
    status: KStatus,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    contentDescription: String? = null,
) {
    val c = MaterialTheme.colorScheme
    val k = KompoundTheme.tokens.colors
    val strings = KompoundTheme.strings
    val text = contentDescription ?: when (status) {
        KStatus.Waiting -> strings.stepWaiting
        KStatus.Running -> strings.stepInProgress
        KStatus.Success -> strings.stepDone
        KStatus.Error -> strings.statusFailed
        KStatus.Stopped -> strings.statusStopped
    }
    val described = if (text.isEmpty()) modifier else modifier.semantics { this.contentDescription = text }
    when (status) {
        KStatus.Waiting -> KCircularProgress(0f, described, size = size, strokeWidth = 2.dp, color = c.onSurfaceVariant)
        KStatus.Running -> KCircularProgress(null, described, size = size, strokeWidth = 2.dp, color = c.primary)
        KStatus.Success -> KIcon(KIcons.Check, null, described.size(size), tint = k.success)
        KStatus.Error -> KIcon(KIcons.Close, null, described.size(size), tint = c.error)
        KStatus.Stopped -> Box(described.size(size), contentAlignment = Alignment.Center) {
            Box(Modifier.width(size * 0.5f).height(2.dp).clip(CircleShape).background(c.onSurfaceVariant))
        }
    }
}
