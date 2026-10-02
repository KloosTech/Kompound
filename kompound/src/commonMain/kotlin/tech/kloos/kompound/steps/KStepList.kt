package tech.kloos.kompound.steps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.progress.KCircularProgress
import tech.kloos.kompound.progress.KLinearProgress
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.math.roundToInt

/** Where a [KStep] is. */
@Immutable
public sealed interface KStepState {
    /** Not started. */
    public data object Waiting : KStepState

    /** Running; [progress] is a fraction from 0 to 1, or `null` when the amount is unknown. */
    public data class InProgress(val progress: Float? = null) : KStepState

    /** Finished. */
    public data object Done : KStepState
}

/**
 * One step of a [KStepList].
 *
 * @property title What the step does.
 * @property state Where it is.
 * @property trailing Optional text after the status icon, such as the remaining or elapsed time.
 */
@Immutable
public class KStep(public val title: String, public val state: KStepState = KStepState.Waiting, public val trailing: String? = null)

/** Words screen readers use for the three states; replace them to localise. */
@Immutable
public class KStepLabels(public val waiting: String = "Waiting", public val inProgress: String = "In progress", public val done: String = "Done")

/**
 * A vertical list of steps of a longer task (an update, an import, a checkout), each with a progress line,
 * a status icon (empty ring, spinner or check mark) and an optional trailing note. Steps change colour from
 * quiet through the accent to success as they run and finish.
 *
 * @param steps The steps in order.
 * @param modifier Modifier applied to the outermost node.
 * @param labels Accessibility words for the states.
 * @param style Overrides merged over a transparent container.
 */
@Composable
public fun KStepList(
    steps: List<KStep>,
    modifier: Modifier = Modifier,
    labels: KStepLabels = KStepLabels(),
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    Column(modifier.fillMaxWidth().styleable(state, style), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEach { Step(it, labels) }
    }
}

@Composable
private fun Step(step: KStep, labels: KStepLabels) {
    val c = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val k = KompoundTheme.tokens.colors
    val (accent, fraction, description) = when (val s = step.state) {
        KStepState.Waiting -> Triple(c.onSurfaceVariant, 0f, labels.waiting)
        is KStepState.InProgress -> Triple(c.primary, s.progress ?: 0f, labels.inProgress + (s.progress?.let { ", " + (it * 100).roundToInt() + "%" } ?: ""))
        KStepState.Done -> Triple(k.success, 1f, labels.done)
    }
    val titleStyle = type.titleSmall.copy(color = if (step.state is KStepState.Waiting) c.onSurface else accent)
    val shown by animateFloatAsState(fraction, tween(300, easing = LinearEasing), label = "step")
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = step.title; stateDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KLinearProgress(if (step.state is KStepState.InProgress && step.state.progress == null) null else shown, Modifier.fillMaxWidth(), color = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(step.state is KStepState.Done, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "icon") { done ->
                if (done) KIcon(KompoundIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                else KCircularProgress(if (step.state is KStepState.InProgress) step.state.progress else 0f, size = 20.dp, strokeWidth = 3.dp, color = accent)
            }
            if (step.trailing != null) KText(step.trailing, style = Style { contentColor(accent); textStyle(type.bodyMedium.copy(color = accent, fontFeatureSettings = "tnum")) })
            KText(step.title, Modifier.weight(1f), maxLines = 1, style = Style { contentColor(accent); textStyle(titleStyle) })
        }
    }
}
