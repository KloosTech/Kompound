package tech.kloos.kompound.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import tech.kloos.kompound.json.KJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KThemePresetsTest {
    private fun pairs(s: ColorScheme) = mapOf(
        "primary" to (s.onPrimary to s.primary),
        "primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "secondary" to (s.onSecondary to s.secondary),
        "secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "tertiary" to (s.onTertiary to s.tertiary),
        "tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "error" to (s.onError to s.error),
        "errorContainer" to (s.onErrorContainer to s.errorContainer),
        "surface" to (s.onSurface to s.surface),
        "background" to (s.onBackground to s.background),
        "surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
        "surfaceContainer" to (s.onSurface to s.surfaceContainer),
        "surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        "inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
    )

    @Test
    fun textOnEveryColourOfEveryPresetHasAtLeast4Point5ToOneContrastInLightAndDark() {
        for (preset in KThemePresets.all) for (dark in listOf(false, true)) {
            for ((role, pair) in pairs(preset.colorScheme(dark))) {
                val ratio = KThemePresets.contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5f, "${preset.name} ${if (dark) "dark" else "light"} $role: $ratio")
            }
        }
    }

    @Test
    fun theHighContrastPresetReaches7ToOneForBodyText() {
        for (dark in listOf(false, true)) {
            val s = KThemePresets.HighContrast.colorScheme(dark)
            assertTrue(KThemePresets.contrast(s.onSurface, s.surface) >= 7f)
            assertTrue(KThemePresets.contrast(s.onPrimary, s.primary) >= 7f, "primary button text")
            assertTrue(KThemePresets.contrast(s.outline, s.surface) >= 7f, "outline")
        }
    }

    @Test
    fun presetsDifferAndSeedsMakeTheGivenPrimaryFamily() {
        assertEquals(6, KThemePresets.all.map { it.name }.toSet().size)
        assertEquals(6, KThemePresets.all.map { it.light.primary }.toSet().size, "every preset has its own primary")
        val custom = KThemePresets.fromSeeds("Mine", primary = Color(0xFFD1581B))
        assertEquals("Mine", custom.name)
        assertNotEquals(custom.light.primary, custom.dark.primary)
        assertTrue(custom.light.primary.red > custom.light.primary.blue, "an orange seed stays orange")
        assertTrue(custom.dark.surface.red < 0.2f && custom.light.surface.red > 0.9f)
        assertEquals(custom.lightColors, KompoundColors.Light)
    }

    @Test
    fun specGivesTheSchemeAndTokensForTheAppearance() {
        val ocean = KThemePresets.Ocean
        assertEquals(ocean.light, ocean.colorScheme(false))
        assertEquals(ocean.dark, ocean.colorScheme(true))
        assertEquals(KompoundColors.Dark, ocean.tokens(true).colors)
        assertEquals(KompoundColors.Light, ocean.tokens(false).colors)
    }
}

class KThemeImportTest {
    private val materialThemeBuilder = """
        {
          "description": "TYPE: CUSTOM",
          "seed": "#6750A4",
          "schemes": {
            "light": { "primary": "#8F4C38", "onPrimary": "#FFFFFF", "primaryContainer": "#FFDBD0", "surfaceContainerHigh": "#F3E5E0", "mysteryColor": "#123456", "scrim": "#000000" },
            "dark": { "primary": "#FFB5A0", "onPrimary": "#561F0F", "surface": "#1A1110" },
            "light-high-contrast": { "primary": "#401000" }
          }
        }
    """.trimIndent()

    @Test
    fun aMaterialThemeBuilderFileFillsTheSchemesAndReportsWhatItDidNotUse() {
        val result = KThemeImport.fromMaterialThemeBuilder(materialThemeBuilder, "Terracotta")
        val spec = result.theme
        assertEquals("Terracotta", spec.name)
        assertEquals(Color(0xFF8F4C38), spec.light.primary)
        assertEquals(Color(0xFFFFFFFF), spec.light.onPrimary)
        assertEquals(Color(0xFFF3E5E0), spec.light.surfaceContainerHigh)
        assertEquals(Color(0xFFFFB5A0), spec.dark.primary)
        assertEquals(Color(0xFF1A1110), spec.dark.surface)
        assertTrue(result.warnings.any { "mysteryColor" in it }, "unknown keys are reported: ${result.warnings}")
        // roles not in the file keep the Material defaults
        assertNotEquals(spec.light.primary, spec.light.secondary)
    }

    @Test
    fun theContrastVariantIsReadWhenAsked() {
        val high = KThemeImport.fromMaterialThemeBuilder(materialThemeBuilder, contrast = KThemeImport.Contrast.High).theme
        assertEquals(Color(0xFF401000), high.light.primary)
        assertEquals(Color(0xFFFFB5A0), high.dark.primary, "no high-contrast dark scheme: falls back to the standard one")
    }

    @Test
    fun filesThatAreNotThemeBuilderExportsAreRejected() {
        assertFailsWith<KJsonException> { KThemeImport.fromMaterialThemeBuilder("[]") }
        assertFailsWith<KJsonException> { KThemeImport.fromMaterialThemeBuilder("""{"nothing": 1}""") }
        assertFailsWith<KJsonException> { KThemeImport.fromMaterialThemeBuilder("not json") }
    }

    @Test
    fun tokensStudioStyleTokensAreMatchedByTheEndOfTheirPathAndSplitIntoLightAndDark() {
        val json = """
            {
              "color": {
                "light": {
                  "primary": { "value": "#006A60", "type": "color" },
                  "on-primary": { "value": "#FFFFFF", "type": "color" },
                  "Primary Container": { "value": "#74F8E5", "type": "color" },
                  "success": { "value": "#1B7F3B", "type": "color" },
                  "spacing": { "value": "8px", "type": "spacing" }
                },
                "dark": {
                  "primary": { "value": "#53DBC9", "type": "color" },
                  "onPrimary": { "value": "#003731", "type": "color" }
                }
              }
            }
        """.trimIndent()
        val result = KThemeImport.fromDesignTokens(json)
        assertEquals(Color(0xFF006A60), result.theme.light.primary)
        assertEquals(Color(0xFFFFFFFF), result.theme.light.onPrimary)
        assertEquals(Color(0xFF74F8E5), result.theme.light.primaryContainer, "the longest role wins: not mistaken for plain 'container'")
        assertEquals(Color(0xFF53DBC9), result.theme.dark.primary)
        assertEquals(Color(0xFF003731), result.theme.dark.onPrimary)
        assertEquals(Color(0xFF1B7F3B), result.theme.lightColors.success)
        assertTrue(result.warnings.none { "spacing" in it }, "a non-colour token is not worth a warning")
    }

    @Test
    fun w3cTokensWithReferencesAndMdSysNamesWork() {
        val json = """
            {
              "ref": { "palette": { "teal40": { "${'$'}value": "#006A60", "${'$'}type": "color" } } },
              "md": { "sys": { "color": {
                "primary": { "${'$'}value": "{ref.palette.teal40}" },
                "on-surface": { "${'$'}value": "rgb(25, 28, 27)" },
                "surface-container-lowest": { "${'$'}value": "#FFF" }
              } } }
            }
        """.trimIndent()
        val theme = KThemeImport.fromDesignTokens(json, "W3C").theme
        assertEquals(Color(0xFF006A60), theme.light.primary, "reference followed")
        assertEquals(Color(25, 28, 27), theme.light.onSurface)
        assertEquals(Color(0xFFFFFFFF), theme.light.surfaceContainerLowest)
    }

    @Test
    fun emptyOrMissingTokenFilesAreRejectedAndMissingModesAreReported() {
        assertFailsWith<KJsonException> { KThemeImport.fromDesignTokens("""{"a": {"b": 1}}""") }
        val onlyLight = KThemeImport.fromDesignTokens("""{"primary": {"value": "#336699"}}""")
        assertTrue(onlyLight.warnings.any { "dark" in it })
    }

    @Test
    fun colourTextInTheCommonCssForms() {
        assertEquals(Color(0xFF112233), KThemeImport.parseColor("#123"))
        assertEquals(Color(0xFF123456), KThemeImport.parseColor("  #123456 "))
        assertEquals(Color(0x80123456), KThemeImport.parseColor("#12345680"))
        assertEquals(Color(0x88112233), KThemeImport.parseColor("#1238"))
        assertEquals(Color(10, 20, 30), KThemeImport.parseColor("rgb(10, 20, 30)"))
        assertEquals(Color(10, 20, 30, 127), KThemeImport.parseColor("rgba(10,20,30,0.5)"))
        assertEquals(Color(255, 0, 0), KThemeImport.parseColor("rgb(100%, 0%, 0%)"))
        for (bad in listOf("", "red", "#12", "#GGGGGG", "rgb(1,2)", "rgb(a,b,c)", "8px")) assertNull(KThemeImport.parseColor(bad), bad)
    }
}
