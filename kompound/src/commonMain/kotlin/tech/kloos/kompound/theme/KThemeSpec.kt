package tech.kloos.kompound.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable

/**
 * A complete colour theme: a light and a dark Material 3 `ColorScheme` plus the extra Kompound colours (success, warning, info) for each.
 * Use a preset from [KThemePresets], import one from a design tool with [KThemeImport], or build your own, and hand it to
 * `KompoundTheme(spec)`.
 *
 * @property name Shown in pickers.
 */
@Immutable
public class KThemeSpec(
    public val name: String,
    public val light: ColorScheme,
    public val dark: ColorScheme,
    public val lightColors: KompoundColors = KompoundColors.Light,
    public val darkColors: KompoundColors = KompoundColors.Dark,
) {
    /** The Material 3 colour scheme for the light or dark appearance. */
    public fun colorScheme(dark: Boolean): ColorScheme = if (dark) this.dark else light

    /** The Kompound tokens (extra colours) for the light or dark appearance. */
    public fun tokens(dark: Boolean): KompoundTokens = KompoundTokens(colors = if (dark) darkColors else lightColors)

    /** A copy with some parts replaced. */
    public fun copy(
        name: String = this.name,
        light: ColorScheme = this.light,
        dark: ColorScheme = this.dark,
        lightColors: KompoundColors = this.lightColors,
        darkColors: KompoundColors = this.darkColors,
    ): KThemeSpec = KThemeSpec(name, light, dark, lightColors, darkColors)
}
