package tech.kloos.kompound.snackbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.coroutines.resume

/** How long a snackbar stays: [Short] 4s, [Long] 10s, or until dismissed or acted on ([Indefinite]). */
public enum class KSnackbarDuration(internal val millis: Long?) {
    Short(4_000), Long(10_000), Indefinite(null)
}

/** Meaning of a snackbar; picks its colours. [Neutral] is the inverse surface, the others are solid colours. */
public enum class KSnackbarTone { Neutral, Info, Success, Warning, Error }

/** How a snackbar ended. */
public enum class KSnackbarResult { Dismissed, ActionPerformed }

/** One message shown by a [KSnackbarHost]. */
@Stable
public interface KSnackbarData {
    public val message: String
    public val actionLabel: String?
    public val tone: KSnackbarTone
    public val duration: KSnackbarDuration

    /** Ends the snackbar as [KSnackbarResult.ActionPerformed]. */
    public fun performAction()

    /** Ends the snackbar as [KSnackbarResult.Dismissed]. */
    public fun dismiss()
}

/**
 * State of a [KSnackbarHost]: call [showSnackbar] from a coroutine. Messages are queued, so a second call
 * suspends until the first snackbar is gone.
 */
@Stable
public class KSnackbarHostState {
    private val mutex = Mutex()

    /** The snackbar being shown, or `null`. */
    public var currentSnackbar: KSnackbarData? by mutableStateOf(null)
        private set

    /**
     * Shows a snackbar and suspends until it ends. If the calling coroutine is cancelled the snackbar is
     * removed.
     *
     * @return [KSnackbarResult.ActionPerformed] when the user pressed the action, otherwise [KSnackbarResult.Dismissed].
     */
    public suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        tone: KSnackbarTone = KSnackbarTone.Neutral,
        duration: KSnackbarDuration = if (actionLabel == null) KSnackbarDuration.Short else KSnackbarDuration.Long,
    ): KSnackbarResult = mutex.withLock {
        try {
            suspendCancellableCoroutine { continuation ->
                currentSnackbar = Data(message, actionLabel, tone, duration, continuation)
            }
        } finally {
            currentSnackbar = null
        }
    }

    private class Data(
        override val message: String,
        override val actionLabel: String?,
        override val tone: KSnackbarTone,
        override val duration: KSnackbarDuration,
        private val continuation: CancellableContinuation<KSnackbarResult>,
    ) : KSnackbarData {
        override fun performAction() { if (continuation.isActive) continuation.resume(KSnackbarResult.ActionPerformed) }
        override fun dismiss() { if (continuation.isActive) continuation.resume(KSnackbarResult.Dismissed) }
    }
}

/**
 * Shows the snackbars of [hostState] with a fade. Place it in the [tech.kloos.kompound.scaffold.KScaffold]
 * `snackbarHost` slot (or anywhere near the bottom of the screen). It dismisses each snackbar after its
 * duration, and announces it to screen readers.
 *
 * @param hostState The state to observe.
 * @param modifier Modifier applied to the host.
 * @param snackbar How to draw one snackbar; defaults to [KSnackbar].
 */
@Composable
public fun KSnackbarHost(
    hostState: KSnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (KSnackbarData) -> Unit = { KSnackbar(it) },
) {
    val current = hostState.currentSnackbar
    LaunchedEffect(current) {
        val millis = current?.duration?.millis
        if (current != null && millis != null) {
            delay(millis)
            current.dismiss()
        }
    }
    AnimatedContent(
        targetState = current,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(75)) },
        contentAlignment = Alignment.BottomCenter,
    ) { data -> if (data != null) snackbar(data) }
}

/**
 * One snackbar: a message and an optional action button.
 *
 * @param data The message to show; its action calls [KSnackbarData.performAction].
 * @param modifier Modifier applied to the snackbar.
 * @param style Overrides merged over [KSnackbarDefaults.style].
 */
@Composable
public fun KSnackbar(
    data: KSnackbarData,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val colors = KSnackbarDefaults.colors(data.tone)
    val type = MaterialTheme.typography
    val actionStyle = remember(colors.action, type) { Style { contentColor(colors.action); textStyle(type.labelLarge.copy(color = colors.action)) } }
    Row(
        modifier = modifier
            .widthIn(min = 288.dp, max = 560.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .styleable(state, KSnackbarDefaults.style(data.tone), style),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KText(data.message, Modifier.weight(1f, fill = true), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (data.actionLabel != null) {
            KButton(
                onClick = { data.performAction() },
                variant = KButtonVariant.Text,
                style = actionStyle,
            ) { KText(data.actionLabel!!) }
        }
    }
}

/** Colours of a snackbar tone. */
public class KSnackbarColors(public val container: Color, public val content: Color, public val action: Color)

/** Defaults for [KSnackbar]. */
public object KSnackbarDefaults {
    /** Container, content and action colours for [tone]. */
    @Composable
    public fun colors(tone: KSnackbarTone): KSnackbarColors {
        val c = MaterialTheme.colorScheme
        val k = KompoundTheme.tokens.colors
        return when (tone) {
            KSnackbarTone.Neutral -> KSnackbarColors(c.inverseSurface, c.inverseOnSurface, c.inversePrimary)
            KSnackbarTone.Info -> KSnackbarColors(k.info, k.onInfo, k.onInfo)
            KSnackbarTone.Success -> KSnackbarColors(k.success, k.onSuccess, k.onSuccess)
            KSnackbarTone.Warning -> KSnackbarColors(k.warning, k.onWarning, k.onWarning)
            KSnackbarTone.Error -> KSnackbarColors(c.error, c.onError, c.onError)
        }
    }

    /** Base style: rounded container in the tone's colour, body text, 16dp horizontal padding, 48dp minimum height. */
    @Composable
    public fun style(tone: KSnackbarTone = KSnackbarTone.Neutral): Style {
        val colors = colors(tone)
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        return remember(colors.container, colors.content, shapes, type) {
            Style {
                background(colors.container)
                contentColor(colors.content)
                textStyle(type.bodyMedium.copy(color = colors.content))
                shape(shapes.small)
                contentPadding(horizontal = 16.dp, vertical = 4.dp)
                minHeight(48.dp)
            }
        }
    }
}
