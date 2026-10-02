package tech.kloos.kompound.dialog

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.internal.KompoundIcons
import tech.kloos.kompound.text.KText

/**
 * Modal dialog: a [title], scrollable [content] and an [actions] footer on a surface above a dimmed scrim.
 * Show it by calling it from composition while it should be visible (`if (open) KDialog(...)`); the caller
 * owns the state, and [onDismissRequest] is called for Escape/back, a click on the scrim and the close
 * button.
 *
 * @param onDismissRequest Called when the user asks to close the dialog.
 * @param modifier Modifier applied to the dialog surface.
 * @param title Optional title text, announced as a heading.
 * @param actions Optional footer, usually buttons; laid out at the end with 8dp between them.
 * @param fullScreen When true the dialog fills the window (no scrim, no rounded corners) and avoids
 * [contentWindowInsets].
 * @param showCloseButton Shows a close icon button next to the title.
 * @param closeContentDescription Accessibility description of the close button (pass a localised string).
 * @param dismissOnBackPress Whether Escape/back dismisses.
 * @param dismissOnClickOutside Whether a click on the scrim dismisses (ignored for [fullScreen]).
 * @param contentWindowInsets Insets the full-screen dialog avoids; defaults to all system bars.
 * @param style Overrides merged over [KDialogDefaults.style].
 * @param content Body of the dialog; scrolls when taller than the window.
 */
@Composable
public fun KDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    fullScreen: Boolean = false,
    showCloseButton: Boolean = false,
    closeContentDescription: String = "Close",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    style: Style = Style,
    content: @Composable ColumnScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val surfaceState = remember { MutableStyleState(null) }
    val titleState = remember { MutableStyleState(null) }
    val scrim = MaterialTheme.colorScheme.scrim.let { KDialogDefaults.scrim(it) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = false,        // handled by our own scrim so the surface can swallow its clicks
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(if (fullScreen) androidx.compose.ui.graphics.Color.Transparent else scrim)
                .pointerInput(dismissOnClickOutside, fullScreen) {
                    if (dismissOnClickOutside && !fullScreen) detectTapGestures { onDismissRequest() }
                },
            contentAlignment = Alignment.Center,
        ) {
            val sizing = if (fullScreen) {
                Modifier.fillMaxSize().windowInsetsPadding(contentWindowInsets)
            } else {
                Modifier.widthIn(min = KDialogDefaults.MinWidth, max = KDialogDefaults.MaxWidth)
                    .heightIn(max = maxHeight * KDialogDefaults.MaxHeightFraction)
            }
            Column(
                modifier = modifier
                    .then(sizing)
                    .pointerInput(Unit) { detectTapGestures { } }       // clicks on the surface must not reach the scrim
                    .styleable(surfaceState, KDialogDefaults.style(fullScreen), style),
                verticalArrangement = Arrangement.spacedBy(KDialogDefaults.SectionSpacing),
            ) {
                if (title != null || showCloseButton) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (title != null) {
                            KText(title, Modifier.weight(1f).semantics { heading() }.styleable(titleState, KDialogDefaults.titleStyle()))
                        } else {
                            Box(Modifier.weight(1f))
                        }
                        if (showCloseButton) {
                            KIconButton(onClick = onDismissRequest, contentDescription = closeContentDescription) {
                                KIcon(KompoundIcons.Close, contentDescription = null)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f, fill = fullScreen).verticalScroll(rememberScrollState()), content = content)
                if (actions != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        actions()
                    }
                }
            }
        }
    }
}

/**
 * Standard confirm/cancel dialog with a [title] and a [message].
 *
 * @param onDismissRequest Called when the user dismisses the dialog (also by the dismiss button).
 * @param title Title of the dialog.
 * @param message Body text.
 * @param confirmText Label of the primary button (pass a localised string).
 * @param onConfirm Called when the primary button is pressed; the caller closes the dialog.
 * @param dismissText Label of the secondary button; `null` hides it.
 */
@Composable
public fun KAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
) {
    KDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        actions = {
            if (dismissText != null) KButton(onClick = onDismissRequest, variant = KButtonVariant.Text) { KText(dismissText) }
            KButton(onClick = onConfirm, variant = KButtonVariant.Text) { KText(confirmText) }
        },
    ) { KText(message) }
}

/** Defaults for [KDialog]. */
public object KDialogDefaults {
    /** Smallest width of a non-full-screen dialog. */
    public val MinWidth: Dp = 280.dp

    /** Largest width of a non-full-screen dialog. */
    public val MaxWidth: Dp = 560.dp

    /** Largest height of a non-full-screen dialog as a fraction of the window. */
    public const val MaxHeightFraction: Float = 0.9f

    /** Gap between title, content and footer. */
    public val SectionSpacing: Dp = 16.dp

    /** The dimming colour behind a dialog: the theme's scrim at 32%. */
    public fun scrim(scrim: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color = scrim.copy(alpha = 0.32f)

    /** Surface: `surfaceContainerHigh`, 28dp corners (square when [fullScreen]), 24dp padding, body text. */
    @Composable
    public fun style(fullScreen: Boolean = false): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        val type = MaterialTheme.typography
        return remember(c, shapes, type, fullScreen) {
            Style {
                background(c.surfaceContainerHigh)
                contentColor(c.onSurface)
                textStyle(type.bodyMedium.copy(color = c.onSurface))
                shape(if (fullScreen) RectangleShape else shapes.extraLarge)
                contentPadding(24.dp)
            }
        }
    }

    /** Title: headline-small in `onSurface`. */
    @Composable
    public fun titleStyle(): Style {
        val c = MaterialTheme.colorScheme
        val type = MaterialTheme.typography
        return remember(c, type) { Style { contentColor(c.onSurface); textStyle(type.headlineSmall.copy(color = c.onSurface)) } }
    }
}
