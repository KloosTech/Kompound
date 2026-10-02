package tech.kloos.kompound.catalog.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeSettingsTest {
    private val settings = ThemeSettings.Presets.flatMap { p ->
        listOf(0.2f, 1f).map { sat -> ThemeSettings(hue = p.hue, saturation = (p.saturation * sat).coerceIn(0.05f, 1f)) }
    }

    @Test
    fun hslRoundTrips() {
        for (c in listOf(Color(0xFF6750A4), Color(0xFF00695C), Color(0xFFD32F2F), Color.White, Color.Black, Color(0xFF808080))) {
            val back = hsl(c.toHsl().h, c.toHsl().s, c.toHsl().l)
            assertTrue(abs(back.red - c.red) < 0.01f && abs(back.green - c.green) < 0.01f && abs(back.blue - c.blue) < 0.01f, "$c -> $back")
        }
    }

    @Test
    fun contrastMatchesKnownValues() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
        assertTrue(contrastRatio(Color(0xFF777777), Color.White) in 4.4f..4.6f)
    }

    @Test
    fun onColorPicksTheReadableSide() {
        assertEquals(Color.White, onColorFor(Color(0xFF123456)))
        assertTrue(onColorFor(Color(0xFFFFEB3B)) != Color.White)
    }

    @Test
    fun ensureContrastDarkensUntilReadable() {
        val result = ensureContrast(Color(0xFF9E9E9E), Color.White, 4.5f, darker = true)
        assertTrue(contrastRatio(result, Color.White) >= 4.5f)
    }

    @Test
    fun everyDerivedSchemeIsReadableInLightAndDark() {
        for (s in settings) for (dark in listOf(false, true)) {
            val c = s.colorScheme(dark)
            val label = "hue=${s.hue} sat=${s.saturation} dark=$dark"
            fun check(name: String, fg: Color, bg: Color, min: Float) =
                assertTrue(contrastRatio(fg, bg) >= min, "$label $name ${contrastRatio(fg, bg)} < $min")
            check("primary", c.onPrimary, c.primary, 4.5f)
            check("primaryContainer", c.onPrimaryContainer, c.primaryContainer, 4.5f)
            check("secondary", c.onSecondary, c.secondary, 4.5f)
            check("secondaryContainer", c.onSecondaryContainer, c.secondaryContainer, 4.5f)
            check("tertiary", c.onTertiary, c.tertiary, 4.5f)
            check("tertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer, 4.5f)
            check("surface text", c.onSurface, c.surface, 7f)
            check("variant text on the darkest container", c.onSurfaceVariant, c.surfaceContainerHighest, 4.5f)
            check("outline is visible", c.outline, c.surface, 3f)
            check("primary on surface (filled controls)", c.primary, c.surface, 3f)
            check("inverse", c.inverseOnSurface, c.inverseSurface, 7f)
        }
    }

    @Test
    fun darkAndLightAreActuallyDifferent() {
        val s = ThemeSettings()
        assertTrue(s.colorScheme(false).surface.toHsl().l > 0.9f)
        assertTrue(s.colorScheme(true).surface.toHsl().l < 0.15f)
    }

    @Test
    fun modeResolvesAgainstTheSystem() {
        assertEquals(true, ThemeSettings(mode = ThemeMode.System).isDark(true))
        assertEquals(false, ThemeSettings(mode = ThemeMode.System).isDark(false))
        assertEquals(false, ThemeSettings(mode = ThemeMode.Light).isDark(true))
        assertEquals(true, ThemeSettings(mode = ThemeMode.Dark).isDark(false))
    }

    @Test
    fun roundnessScalesTheCornerRadii() {
        val square = ThemeSettings(roundness = 0f).shapes()
        val default = ThemeSettings(roundness = 1f).shapes()
        val round = ThemeSettings(roundness = 2f).shapes()
        assertTrue(square.medium != default.medium && default.medium != round.medium)
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(12.dp), default.medium)
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(24.dp), round.medium)
    }

    @Test
    fun generatedKotlinContainsBothSchemesShapesAndTheThemeCall() {
        val code = ThemeSettings(hue = 140f, saturation = 0.5f, roundness = 1.5f).toKotlin()
        assertTrue("val LightColors = lightColorScheme(" in code)
        assertTrue("val DarkColors = darkColorScheme(" in code)
        assertTrue("primary = Color(0xFF" in code)
        assertTrue("RoundedCornerShape(18.dp)" in code, "medium radius 12 * 1.5 = 18")
        assertTrue("KompoundTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, shapes = AppShapes)" in code)
        val primaryHex = ThemeSettings(hue = 140f, saturation = 0.5f).colorScheme(false).primary.toKotlin()
        assertTrue(primaryHex in code, "the code uses the real derived primary")
    }

    @Test
    fun colourLiteralsAreUppercaseSixDigitHex() {
        assertEquals("Color(0xFF6750A4)", Color(0xFF6750A4).toKotlin())
        assertEquals("Color(0xFF000000)", Color.Black.toKotlin())
        assertEquals("Color(0xFFFFFFFF)", Color.White.toKotlin())
    }
}
