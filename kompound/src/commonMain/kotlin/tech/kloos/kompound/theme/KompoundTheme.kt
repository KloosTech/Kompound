package tech.kloos.kompound.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import tech.kloos.kompound.KompoundStyles

internal val LocalKompoundTokens = compositionLocalOf { KompoundTokens.Light }

/**
 * Content colour for icons (and other non-text content) inside a component. Components that know their
 * content colour provide it here, because inherited Style `contentColor` only reaches text (ADR 0001).
 * [Color.Unspecified] means "use the surface's `onSurface`".
 */
public val LocalKContentColor: androidx.compose.runtime.ProvidableCompositionLocal<Color> =
    compositionLocalOf { Color.Unspecified }

/**
 * Wraps `MaterialTheme` (the token source), provides [KompoundTokens] and a root text style, so plain `KText`
 * on the page uses the theme's text colour. Wrap your app in it (recommended, essential for dark mode); without it
 * components still work from the ambient M3 theme and light tokens, but `KText` outside a styled component is black.
 */
@Composable
public fun KompoundTheme(
    colorScheme: ColorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    typography: Typography = MaterialTheme.typography,
    shapes: Shapes = MaterialTheme.shapes,
    tokens: KompoundTokens = remember(colorScheme) { KompoundTokens.forColorScheme(colorScheme) },
    content: @Composable () -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
        // Root style: text with no styled parent (a plain KText on the page) inherits the theme's text colour and
        // typography instead of falling back to black, which is invisible in dark mode.
        val state = remember { MutableStyleState(null) }
        val base = remember(colorScheme, typography) {
            Style {
                contentColor(colorScheme.onBackground)
                textStyle(typography.bodyMedium.copy(color = colorScheme.onBackground))
            }
        }
        CompositionLocalProvider(LocalKompoundTokens provides tokens) {
            Box(Modifier.styleable(state, base), propagateMinConstraints = true) { content() }
        }
    }
}

/** Accessor for the current Kompound theme values. */
public object KompoundTheme {
    public val tokens: KompoundTokens
        @Composable @ReadOnlyComposable get() = LocalKompoundTokens.current
}
