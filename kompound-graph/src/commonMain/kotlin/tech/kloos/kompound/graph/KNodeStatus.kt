package tech.kloos.kompound.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.progress.KCircularProgress
import tech.kloos.kompound.theme.KompoundTheme

/** The progress mark [KNode] shows in its title bar (see the `nodeStatus` parameter of [KNodeGraph]). */
public enum class KNodeStatus(internal val description: String) {
    /** Queued behind other nodes. */
    Waiting("Waiting"),

    /** Working right now (a spinner). */
    Running("Running"),

    /** Finished (a check). */
    Done("Done"),

    /** Failed (a cross). */
    Failed("Failed"),

    /** Cannot run because something upstream failed. */
    Blocked("Blocked"),

    /** Uses pinned data instead of running. */
    Pinned("Pinned"),

    /** Was cancelled before it finished. */
    Cancelled("Cancelled"),

    /** Was not started because the app refused it (see `GraphEngine`'s `beforeRun`). */
    Declined("Not run"),
}

internal val LocalNodeStatus = compositionLocalOf<((GraphNode) -> KNodeStatus?)?> { null }

/** The small mark for [status]: a spinner while running, otherwise a drawn glyph in a status colour. */
@Composable
internal fun KNodeStatusBadge(status: KNodeStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val success = KompoundTheme.tokens.colors.success
    val tone: Color = when (status) {
        KNodeStatus.Done -> success
        KNodeStatus.Failed, KNodeStatus.Blocked -> scheme.error
        KNodeStatus.Pinned -> scheme.primary
        else -> scheme.outline
    }
    val semanticsModifier = modifier.semantics { contentDescription = status.description }
    if (status == KNodeStatus.Running) {
        KCircularProgress(null, semanticsModifier, size = 14.dp, strokeWidth = 2.dp)
        return
    }
    Canvas(semanticsModifier.size(14.dp)) {
        val w = 1.8.dp.toPx()
        val stroke = Stroke(w, cap = StrokeCap.Round)
        val s = size.minDimension
        when (status) {
            KNodeStatus.Done -> {
                drawLine(tone, Offset(s * 0.15f, s * 0.55f), Offset(s * 0.4f, s * 0.8f), w, StrokeCap.Round)
                drawLine(tone, Offset(s * 0.4f, s * 0.8f), Offset(s * 0.88f, s * 0.2f), w, StrokeCap.Round)
            }
            KNodeStatus.Failed -> {
                drawLine(tone, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), w, StrokeCap.Round)
                drawLine(tone, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f), w, StrokeCap.Round)
            }
            KNodeStatus.Blocked -> {
                drawCircle(tone, s * 0.42f, center, style = stroke)
                drawLine(tone, Offset(s * 0.25f, s * 0.75f), Offset(s * 0.75f, s * 0.25f), w, StrokeCap.Round)
            }
            KNodeStatus.Pinned -> {
                drawCircle(tone, s * 0.22f, Offset(s * 0.5f, s * 0.35f))
                drawLine(tone, Offset(s * 0.5f, s * 0.5f), Offset(s * 0.5f, s * 0.92f), w, StrokeCap.Round)
            }
            KNodeStatus.Declined -> drawCircle(tone, s * 0.38f, center, style = stroke)
            KNodeStatus.Cancelled -> {
                drawLine(tone, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.8f, s * 0.5f), w, StrokeCap.Round)
            }
            else -> {
                // Waiting: three dots.
                for (i in 0..2) drawCircle(tone, w * 0.6f, Offset(s * (0.2f + 0.3f * i), s * 0.5f))
            }
        }
    }
}
