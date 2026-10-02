package tech.kloos.kompound.catalog.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode { System, Light, Dark }

/** A named starting point in the theme designer. */
class ThemePreset(val name: String, val hue: Float, val saturation: Float)

/**
 * What a visitor can change in the catalog's theme designer. Everything is derived from a hue and a
 * saturation, so any combination yields a complete, contrast-checked Material 3 [ColorScheme] for light and dark.
 *
 * @param hue 0..360
 * @param saturation 0..1: how colourful the primary colour is
 * @param roundness 0..2: multiplies the default corner radii (1 = Material defaults, 0 = square)
 * @param textScale 0.8..1.4: multiplies font sizes
 */
@Immutable
data class ThemeSettings(
    val hue: Float = 262f,
    val saturation: Float = 0.55f,
    val roundness: Float = 1f,
    val textScale: Float = 1f,
    val mode: ThemeMode = ThemeMode.System,
) {
    /** The colour shown as the theme's swatch. */
    val seed: Color get() = hsl(hue, saturation.coerceAtLeast(0.15f), 0.5f)

    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    fun colorScheme(dark: Boolean): ColorScheme = if (dark) darkScheme() else lightScheme()

    /** Material shapes with every default radius multiplied by [roundness]. */
    fun shapes(): Shapes {
        fun r(base: Float) = RoundedCornerShape((base * roundness).dp)
        return Shapes(extraSmall = r(4f), small = r(8f), medium = r(12f), large = r(16f), extraLarge = r(28f))
    }

    private val tintSat get() = 0.05f + 0.10f * saturation.coerceIn(0f, 1f)

    private fun lightScheme(): ColorScheme {
        val h = hue
        val s = saturation.coerceIn(0f, 1f)
        val surface = hsl(h, tintSat, 0.985f)
        val primary = ensureContrast(hsl(h, s, 0.42f), Color.White, 4.5f, darker = true)
        val secondary = ensureContrast(hsl(h, s * 0.35f, 0.40f), Color.White, 4.5f, darker = true)
        val tertiary = ensureContrast(hsl(h + 60f, (s * 0.55f).coerceAtLeast(0.2f), 0.40f), Color.White, 4.5f, darker = true)
        val primaryContainer = hsl(h, (s * 0.9f).coerceAtMost(1f), 0.90f)
        val secondaryContainer = hsl(h, s * 0.3f, 0.90f)
        val tertiaryContainer = hsl(h + 60f, (s * 0.5f).coerceAtLeast(0.2f), 0.90f)
        val darkPrimary = ensureContrast(hsl(h, s, 0.78f), hsl(h, tintSat, 0.07f), 4.5f, darker = false)
        return lightColorScheme(
            primary = primary, onPrimary = onColorFor(primary),
            primaryContainer = primaryContainer, onPrimaryContainer = ensureContrast(hsl(h, s, 0.18f), primaryContainer, 7f, true),
            inversePrimary = darkPrimary,
            secondary = secondary, onSecondary = onColorFor(secondary),
            secondaryContainer = secondaryContainer, onSecondaryContainer = ensureContrast(hsl(h, s * 0.4f, 0.18f), secondaryContainer, 7f, true),
            tertiary = tertiary, onTertiary = onColorFor(tertiary),
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = ensureContrast(hsl(h + 60f, s * 0.6f, 0.18f), tertiaryContainer, 7f, true),
            background = surface, onBackground = hsl(h, tintSat, 0.12f),
            surface = surface, onSurface = hsl(h, tintSat, 0.12f),
            surfaceVariant = hsl(h, tintSat * 1.2f, 0.90f), onSurfaceVariant = hsl(h, tintSat, 0.30f),
            surfaceTint = primary,
            inverseSurface = hsl(h, tintSat, 0.20f), inverseOnSurface = hsl(h, tintSat, 0.95f),
            outline = hsl(h, tintSat, 0.45f), outlineVariant = hsl(h, tintSat, 0.80f),
            scrim = Color.Black,
            surfaceBright = surface, surfaceDim = hsl(h, tintSat, 0.88f),
            surfaceContainerLowest = Color.White, surfaceContainerLow = hsl(h, tintSat, 0.965f),
            surfaceContainer = hsl(h, tintSat, 0.945f), surfaceContainerHigh = hsl(h, tintSat, 0.925f),
            surfaceContainerHighest = hsl(h, tintSat, 0.905f),
        )
    }

    private fun darkScheme(): ColorScheme {
        val h = hue
        val s = saturation.coerceIn(0f, 1f)
        val surface = hsl(h, tintSat, 0.07f)
        val primary = ensureContrast(hsl(h, s, 0.78f), surface, 4.5f, darker = false)
        val secondary = ensureContrast(hsl(h, s * 0.35f, 0.78f), surface, 4.5f, darker = false)
        val tertiary = ensureContrast(hsl(h + 60f, (s * 0.55f).coerceAtLeast(0.2f), 0.78f), surface, 4.5f, darker = false)
        val primaryContainer = hsl(h, s * 0.8f, 0.26f)
        val secondaryContainer = hsl(h, s * 0.25f, 0.28f)
        val tertiaryContainer = hsl(h + 60f, (s * 0.45f).coerceAtLeast(0.2f), 0.28f)
        val lightPrimary = ensureContrast(hsl(h, s, 0.42f), Color.White, 4.5f, darker = true)
        return darkColorScheme(
            primary = primary, onPrimary = onColorFor(primary),
            primaryContainer = primaryContainer, onPrimaryContainer = ensureContrast(hsl(h, s, 0.90f), primaryContainer, 7f, false),
            inversePrimary = lightPrimary,
            secondary = secondary, onSecondary = onColorFor(secondary),
            secondaryContainer = secondaryContainer, onSecondaryContainer = ensureContrast(hsl(h, s * 0.4f, 0.90f), secondaryContainer, 7f, false),
            tertiary = tertiary, onTertiary = onColorFor(tertiary),
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = ensureContrast(hsl(h + 60f, s * 0.6f, 0.90f), tertiaryContainer, 7f, false),
            background = surface, onBackground = hsl(h, tintSat, 0.92f),
            surface = surface, onSurface = hsl(h, tintSat, 0.92f),
            surfaceVariant = hsl(h, tintSat, 0.20f), onSurfaceVariant = hsl(h, tintSat, 0.78f),
            surfaceTint = primary,
            inverseSurface = hsl(h, tintSat, 0.92f), inverseOnSurface = hsl(h, tintSat, 0.20f),
            outline = hsl(h, tintSat, 0.60f), outlineVariant = hsl(h, tintSat, 0.30f),
            scrim = Color.Black,
            surfaceBright = hsl(h, tintSat, 0.20f), surfaceDim = surface,
            surfaceContainerLowest = hsl(h, tintSat, 0.05f), surfaceContainerLow = hsl(h, tintSat, 0.095f),
            surfaceContainer = hsl(h, tintSat, 0.115f), surfaceContainerHigh = hsl(h, tintSat, 0.14f),
            surfaceContainerHighest = hsl(h, tintSat, 0.17f),
        )
    }

    /** Kotlin code that recreates this theme, ready to paste into an app. */
    fun toKotlin(): String = buildString {
        fun scheme(name: String, builder: String, c: ColorScheme) {
            appendLine("val $name = $builder(")
            listOf(
                "primary" to c.primary, "onPrimary" to c.onPrimary, "primaryContainer" to c.primaryContainer,
                "onPrimaryContainer" to c.onPrimaryContainer, "secondary" to c.secondary, "onSecondary" to c.onSecondary,
                "secondaryContainer" to c.secondaryContainer, "onSecondaryContainer" to c.onSecondaryContainer,
                "tertiary" to c.tertiary, "onTertiary" to c.onTertiary, "tertiaryContainer" to c.tertiaryContainer,
                "onTertiaryContainer" to c.onTertiaryContainer, "background" to c.background, "onBackground" to c.onBackground,
                "surface" to c.surface, "onSurface" to c.onSurface, "surfaceVariant" to c.surfaceVariant,
                "onSurfaceVariant" to c.onSurfaceVariant, "outline" to c.outline, "outlineVariant" to c.outlineVariant,
                "inverseSurface" to c.inverseSurface, "inverseOnSurface" to c.inverseOnSurface, "inversePrimary" to c.inversePrimary,
                "surfaceContainerLow" to c.surfaceContainerLow, "surfaceContainer" to c.surfaceContainer,
                "surfaceContainerHigh" to c.surfaceContainerHigh, "surfaceContainerHighest" to c.surfaceContainerHighest,
            ).forEach { (key, color) -> appendLine("    $key = ${color.toKotlin()},") }
            appendLine(")")
            appendLine()
        }
        scheme("LightColors", "lightColorScheme", lightScheme())
        scheme("DarkColors", "darkColorScheme", darkScheme())
        val k = roundness
        fun r(base: Float) = "RoundedCornerShape(${formatDp(base * k)}.dp)"
        appendLine("val AppShapes = Shapes(")
        appendLine("    extraSmall = ${r(4f)}, small = ${r(8f)}, medium = ${r(12f)},")
        appendLine("    large = ${r(16f)}, extraLarge = ${r(28f)},")
        appendLine(")")
        appendLine()
        appendLine("KompoundTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, shapes = AppShapes) {")
        appendLine("    // your app")
        append("}")
    }

    private fun formatDp(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else ((v * 10).toInt() / 10f).toString()

    companion object {
        val Presets: List<ThemePreset> = listOf(
            ThemePreset("Violet", 262f, 0.55f), ThemePreset("Indigo", 232f, 0.62f), ThemePreset("Blue", 211f, 0.78f),
            ThemePreset("Teal", 175f, 0.65f), ThemePreset("Green", 140f, 0.50f), ThemePreset("Amber", 40f, 0.88f),
            ThemePreset("Orange", 20f, 0.85f), ThemePreset("Red", 2f, 0.72f), ThemePreset("Pink", 330f, 0.65f),
            ThemePreset("Slate", 215f, 0.18f),
        )
    }
}
