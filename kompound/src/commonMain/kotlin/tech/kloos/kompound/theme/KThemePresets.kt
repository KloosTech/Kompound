package tech.kloos.kompound.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Ready-made themes. Each is a light and a dark scheme built from a few brand colours with the same structure as Material 3's own
 * (tonal containers, five surface levels, `on` colours that keep text contrast of at least 4.5:1). Pass one to `KompoundTheme(spec)`.
 */
public object KThemePresets {
    /** Material 3's baseline purple. */
    public val Default: KThemeSpec = KThemeSpec("Default", lightColorScheme(), darkColorScheme())

    /** Blue and teal. */
    public val Ocean: KThemeSpec = fromSeeds("Ocean", primary = Color(0xFF0B6FA4), secondary = Color(0xFF3F6B7C), tertiary = Color(0xFF00897B))

    /** Green and olive. */
    public val Forest: KThemeSpec = fromSeeds("Forest", primary = Color(0xFF2E7D32), secondary = Color(0xFF5A6B4C), tertiary = Color(0xFF8D6E2F))

    /** Orange and rose. */
    public val Sunset: KThemeSpec = fromSeeds("Sunset", primary = Color(0xFFD1581B), secondary = Color(0xFF8A5A4B), tertiary = Color(0xFFB0356B))

    /** Neutral greys with one quiet blue accent: stays out of the way of data and code. */
    public val Graphite: KThemeSpec = fromSeeds("Graphite", primary = Color(0xFF4A5B70), secondary = Color(0xFF626B76), tertiary = Color(0xFF6B5F7A), neutralSaturation = 0.03f)

    /** Black, white and strong colours; text and controls reach a contrast of at least 7:1. */
    public val HighContrast: KThemeSpec = KThemeSpec(
        "High contrast",
        lightColorScheme(
            primary = Color(0xFF0033CC), onPrimary = Color.White, primaryContainer = Color(0xFFDDE4FF), onPrimaryContainer = Color(0xFF000A3D),
            secondary = Color(0xFF3D3D3D), onSecondary = Color.White, secondaryContainer = Color(0xFFE2E2E2), onSecondaryContainer = Color.Black,
            tertiary = Color(0xFF6A1B9A), onTertiary = Color.White, tertiaryContainer = Color(0xFFF1DCFF), onTertiaryContainer = Color(0xFF2A0045),
            error = Color(0xFFB00020), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
            background = Color.White, onBackground = Color.Black, surface = Color.White, onSurface = Color.Black,
            surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF1F1F1F), outline = Color.Black, outlineVariant = Color(0xFF595959),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F7), surfaceContainer = Color(0xFFF1F1F1),
            surfaceContainerHigh = Color(0xFFEAEAEA), surfaceContainerHighest = Color(0xFFE2E2E2),
        ),
        darkColorScheme(
            primary = Color(0xFFB3C5FF), onPrimary = Color.Black, primaryContainer = Color(0xFF1E3A99), onPrimaryContainer = Color.White,
            secondary = Color(0xFFD6D6D6), onSecondary = Color.Black, secondaryContainer = Color(0xFF3A3A3A), onSecondaryContainer = Color.White,
            tertiary = Color(0xFFE5B8FF), onTertiary = Color.Black, tertiaryContainer = Color(0xFF5A1F80), onTertiaryContainer = Color.White,
            error = Color(0xFFFFB4AB), onError = Color.Black, errorContainer = Color(0xFF8C0009), onErrorContainer = Color.White,
            background = Color.Black, onBackground = Color.White, surface = Color.Black, onSurface = Color.White,
            surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xFFEDEDED), outline = Color.White, outlineVariant = Color(0xFFB0B0B0),
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A), surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A), surfaceContainerHighest = Color(0xFF242424),
        ),
    )

    /** Every preset, for a picker. */
    public val all: List<KThemeSpec> = listOf(Default, Ocean, Forest, Sunset, Graphite, HighContrast)

    /**
     * Builds a theme from three brand colours: [primary], [secondary] and [tertiary] become tonal palettes (the light scheme uses tone 40 for
     * the colour and 90 for its container, the dark scheme tone 80 and 30), and the surfaces are tinted greys. `on` colours are chosen for contrast.
     *
     * @param neutralSaturation How much colour the greys carry (0 is pure grey).
     */
    public fun fromSeeds(
        name: String,
        primary: Color,
        secondary: Color = primary,
        tertiary: Color = primary,
        neutralSaturation: Float = 0.06f,
    ): KThemeSpec = KThemeSpec(
        name,
        scheme(false, primary, secondary, tertiary, neutralSaturation),
        scheme(true, primary, secondary, tertiary, neutralSaturation),
    )

    private fun scheme(dark: Boolean, primary: Color, secondary: Color, tertiary: Color, neutralSat: Float): ColorScheme {
        val p = Hsl.of(primary)
        val s = Hsl.of(secondary)
        val t = Hsl.of(tertiary)
        val n = Hsl(p.hue, neutralSat, 0.5f)
        fun accent(h: Hsl, tone: Int) = h.copy(saturation = h.saturation.coerceIn(0.25f, 0.85f)).tone(tone)
        fun neutral(tone: Int) = n.tone(tone)
        // The main colour of a role: its tone, moved darker (light scheme, white text) or lighter (dark scheme, dark text) until the text on it reads at 4.5:1.
        fun strong(h: Hsl, start: Int): Color {
            var tone = start
            var colour = accent(h, tone)
            while (tone in 5..95 && contrast(if (dark) Color.Black else Color.White, colour) < 4.5f) {
                tone += if (dark) 2 else -2
                colour = accent(h, tone)
            }
            return colour
        }
        fun on(background: Color): Color {
            val dark = Color(0xFF101010)
            return when {
                contrast(Color.White, background) >= 4.5f -> Color.White
                contrast(dark, background) >= 4.5f -> dark
                contrast(Color.White, background) >= contrast(Color.Black, background) -> Color.White
                else -> Color.Black
            }
        }
        val error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
        val errorContainer = if (dark) Color(0xFF93000A) else Color(0xFFFFDAD6)
        return if (!dark) {
            val prim = strong(p, 40); val primC = accent(p, 90)
            val sec = strong(s, 40); val secC = accent(s, 90)
            val ter = strong(t, 40); val terC = accent(t, 90)
            lightColorScheme(
                primary = prim, onPrimary = on(prim), primaryContainer = primC, onPrimaryContainer = accent(p, 10).ensureContrast(primC),
                secondary = sec, onSecondary = on(sec), secondaryContainer = secC, onSecondaryContainer = accent(s, 10).ensureContrast(secC),
                tertiary = ter, onTertiary = on(ter), tertiaryContainer = terC, onTertiaryContainer = accent(t, 10).ensureContrast(terC),
                error = error, onError = Color.White, errorContainer = errorContainer, onErrorContainer = Color(0xFF410002),
                background = neutral(98), onBackground = neutral(10), surface = neutral(98), onSurface = neutral(10),
                surfaceVariant = neutral(90), onSurfaceVariant = neutral(30), outline = neutral(50), outlineVariant = neutral(80),
                surfaceContainerLowest = Color.White, surfaceContainerLow = neutral(96), surfaceContainer = neutral(94),
                surfaceContainerHigh = neutral(92), surfaceContainerHighest = neutral(90),
                inverseSurface = neutral(20), inverseOnSurface = neutral(95), inversePrimary = accent(p, 80), surfaceTint = prim,
            )
        } else {
            val prim = strong(p, 80); val primC = accent(p, 30)
            val sec = strong(s, 80); val secC = accent(s, 30)
            val ter = strong(t, 80); val terC = accent(t, 30)
            darkColorScheme(
                primary = prim, onPrimary = on(prim), primaryContainer = primC, onPrimaryContainer = accent(p, 90).ensureContrast(primC),
                secondary = sec, onSecondary = on(sec), secondaryContainer = secC, onSecondaryContainer = accent(s, 90).ensureContrast(secC),
                tertiary = ter, onTertiary = on(ter), tertiaryContainer = terC, onTertiaryContainer = accent(t, 90).ensureContrast(terC),
                error = error, onError = Color(0xFF690005), errorContainer = errorContainer, onErrorContainer = Color(0xFFFFDAD6),
                background = neutral(6), onBackground = neutral(90), surface = neutral(6), onSurface = neutral(90),
                surfaceVariant = neutral(30), onSurfaceVariant = neutral(80), outline = neutral(60), outlineVariant = neutral(30),
                surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10), surfaceContainer = neutral(12),
                surfaceContainerHigh = neutral(17), surfaceContainerHighest = neutral(22),
                inverseSurface = neutral(90), inverseOnSurface = neutral(20), inversePrimary = accent(p, 40), surfaceTint = prim,
            )
        }
    }

    /** If this colour has less than 4.5:1 against [background], black or white (whichever contrasts more) is returned. */
    private fun Color.ensureContrast(background: Color): Color =
        if (contrast(this, background) >= 4.5f) this else if (contrast(Color.White, background) >= contrast(Color.Black, background)) Color.White else Color.Black

    internal fun contrast(a: Color, b: Color): Float {
        val l1 = a.luminance()
        val l2 = b.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    /** Hue (0..360), saturation and lightness (0..1): enough colour model to make tonal ramps without a perceptual colour library. */
    internal data class Hsl(val hue: Float, val saturation: Float, val lightness: Float) {
        /** The colour with this hue and saturation at the Material "tone" (0 black .. 100 white), roughly its lightness. */
        fun tone(tone: Int): Color = copy(lightness = tone / 100f).toColor()

        fun toColor(): Color {
            val c = (1f - abs(2f * lightness - 1f)) * saturation
            val x = c * (1f - abs((hue / 60f) % 2f - 1f))
            val m = lightness - c / 2f
            val (r, g, b) = when {
                hue < 60f -> Triple(c, x, 0f)
                hue < 120f -> Triple(x, c, 0f)
                hue < 180f -> Triple(0f, c, x)
                hue < 240f -> Triple(0f, x, c)
                hue < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
        }

        companion object {
            fun of(color: Color): Hsl {
                val r = color.red; val g = color.green; val b = color.blue
                val maxC = max(r, max(g, b)); val minC = min(r, min(g, b))
                val d = maxC - minC
                val l = (maxC + minC) / 2f
                if (d == 0f) return Hsl(0f, 0f, l)
                val s = d / (1f - abs(2f * l - 1f))
                val h = when (maxC) {
                    r -> 60f * (((g - b) / d) % 6f)
                    g -> 60f * ((b - r) / d + 2f)
                    else -> 60f * ((r - g) / d + 4f)
                }
                return Hsl(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
            }
        }
    }
}
