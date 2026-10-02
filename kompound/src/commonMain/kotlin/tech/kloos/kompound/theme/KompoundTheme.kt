package tech.kloos.kompound.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
 * Wraps `MaterialTheme` (the token source) and provides [KompoundTokens]. Optional: components fall back
 * to the ambient M3 theme and light tokens when it is absent.
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
        CompositionLocalProvider(LocalKompoundTokens provides tokens, content = content)
    }
}

/** Accessor for the current Kompound theme values. */
public object KompoundTheme {
    public val tokens: KompoundTokens
        @Composable @ReadOnlyComposable get() = LocalKompoundTokens.current
}
