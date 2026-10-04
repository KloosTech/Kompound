package tech.kloos.kompound.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Placeholder for a screen or list with nothing to show yet: an optional [illustration], a [title], an
 * optional [description] and an optional [action] such as a "Create" button.
 *
 * @param title Short headline, e.g. "No messages".
 * @param modifier Modifier applied to the whole state.
 * @param description Optional explanation of why it is empty and what to do.
 * @param illustration Optional slot above the title, usually an icon or image.
 * @param action Optional slot below the text, usually a `KButton`.
 * @param style Overrides merged over [KStateDefaults.style].
 */
@Composable
public fun KEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    illustration: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    style: Style = Style,
) {
    StateLayout(title, modifier, description, illustration, action, style, announce = false)
}

/**
 * Placeholder for a failure: an error [illustration] (a red error icon unless you pass another), a
 * [title], an optional [description] and a retry button when [onRetry] is set. Screen readers announce
 * it when it appears.
 *
 * @param title Short headline, e.g. "Could not load messages".
 * @param modifier Modifier applied to the whole state.
 * @param description Optional detail about what went wrong.
 * @param onRetry If set, a retry button is shown and calls this.
 * @param retryText Label of the retry button (pass a localised string).
 * @param illustration Replaces the default error icon.
 * @param action Replaces the retry button.
 * @param style Overrides merged over [KStateDefaults.style].
 */
@Composable
public fun KErrorState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
    retryText: String = KompoundTheme.strings.tryAgain,
    illustration: (@Composable () -> Unit)? = {
        CompositionLocalProvider(LocalKContentColor provides MaterialTheme.colorScheme.error) {
            KIcon(KompoundIcons.Error, contentDescription = null, style = Style { size(48.dp) })
        }
    },
    action: (@Composable () -> Unit)? = null,
    style: Style = Style,
) {
    val resolvedAction: (@Composable () -> Unit)? = action ?: onRetry?.let { retry ->
        { KButton(onClick = retry, variant = KButtonVariant.Tonal) { KText(retryText) } }
    }
    StateLayout(title, modifier, description, illustration, resolvedAction, style, announce = true)
}

@Composable
private fun StateLayout(
    title: String,
    modifier: Modifier,
    description: String?,
    illustration: (@Composable () -> Unit)?,
    action: (@Composable () -> Unit)?,
    style: Style,
    announce: Boolean,
) {
    remember { KompoundStyles.ensureEnabled() }
    val state = remember { MutableStyleState(null) }
    val titleState = remember { MutableStyleState(null) }
    val descriptionState = remember { MutableStyleState(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (announce) liveRegion = LiveRegionMode.Polite }
            .styleable(state, KStateDefaults.style(), style),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        illustration?.invoke()
        KText(title, Modifier.styleable(titleState, KStateDefaults.titleStyle()))
        if (description != null) {
            KText(description, Modifier.widthIn(max = 320.dp).styleable(descriptionState, KStateDefaults.descriptionStyle()))
        }
        action?.invoke()
    }
}

/** Defaults for [KEmptyState] and [KErrorState]. */
public object KStateDefaults {
    /** Container: centred column with 24dp padding. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        return remember(c) { Style { contentColor(c.onSurface); contentPadding(24.dp) } }
    }

    /** Title: title-medium, `onSurface`, centred. */
    @Composable
    public fun titleStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurface); textStyle(type.titleMedium.copy(color = c.onSurface, textAlign = TextAlign.Center)) } }
    }

    /** Description: body-medium, `onSurfaceVariant`, centred. */
    @Composable
    public fun descriptionStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurfaceVariant); textStyle(type.bodyMedium.copy(color = c.onSurfaceVariant, textAlign = TextAlign.Center)) } }
    }
}
