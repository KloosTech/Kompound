package tech.kloos.kompound.showcase.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.avatar.KAvatarStatus
import tech.kloos.kompound.badge.KBadge
import tech.kloos.kompound.badge.KBadgeTone
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.i18n.KompoundStrings
import tech.kloos.kompound.json.KJsonException
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.search.KSearchBar
import tech.kloos.kompound.steps.KStep
import tech.kloos.kompound.steps.KStepList
import tech.kloos.kompound.steps.KStepState
import tech.kloos.kompound.surface.KSurface
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextArea
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KDensity
import tech.kloos.kompound.theme.KThemeImport
import tech.kloos.kompound.theme.KThemePresets
import tech.kloos.kompound.theme.KThemeSpec
import tech.kloos.kompound.theme.KompoundTheme

private const val Usage_theme_presets = """import tech.kloos.kompound.i18n.KompoundStrings
import tech.kloos.kompound.theme.KDensity
import tech.kloos.kompound.theme.KThemeImport
import tech.kloos.kompound.theme.KThemePresets
import tech.kloos.kompound.theme.KompoundTheme

// A preset, a density and a language in one place.
KompoundTheme(
    spec = KThemePresets.Ocean,                 // or KThemePresets.fromSeeds("Brand", primary = Color(0xFF0B6FA4))
    density = KDensity.Compact,                 // Compact, Comfortable (default) or Spacious
    strings = KompoundStrings.German,           // default: follows the device language
) { App() }

// Import what a designer exported (Material Theme Builder, or Tokens Studio / W3C design tokens).
val imported = KThemeImport.fromMaterialThemeBuilder(jsonText, name = "Brand")
imported.warnings.forEach(::println)           // keys that were not understood
KompoundTheme(spec = imported.theme) { App() }

// Change one built-in text.
KompoundTheme(strings = KompoundStrings.English.copy(close = "Dismiss")) { App() }"""

private val Languages = listOf("English" to KompoundStrings.English, "Deutsch" to KompoundStrings.German, "Français" to KompoundStrings.French, "Español" to KompoundStrings.Spanish, "Italiano" to KompoundStrings.Italian)

private const val SampleThemeBuilder = """{
  "schemes": {
    "light": { "primary": "#8F4C38", "onPrimary": "#FFFFFF", "primaryContainer": "#FFDBD0", "onPrimaryContainer": "#390C00", "surface": "#FFF8F6", "onSurface": "#231917" },
    "dark": { "primary": "#FFB5A0", "onPrimary": "#561F0F", "primaryContainer": "#723522", "onPrimaryContainer": "#FFDBD0", "surface": "#1A1110", "onSurface": "#F1DFDA" }
  }
}"""

@OptIn(ExperimentalLayoutApi::class)
@KompoundDemo(
    id = "theme.presets",
    title = "Themes, density and language",
    description = "Theme presets, compact to spacious density, built-in translations and import from Material Theme Builder or design tokens.",
    category = KompoundCategory.Foundations,
    tags = ["theme", "preset", "density", "compact", "language", "localization", "import", "figma", "tokens"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_theme_presets,
)
@Composable
fun DemoScope.KThemePresetsDemo() {
    val preset = choiceControl("Preset", KThemePresets.all, label = { it.name })
    val dark = boolControl("Dark", false)
    val density = choiceControl("Density", KDensity.entries, KDensity.Comfortable)
    val language = choiceControl("Language", Languages, label = { it.first })
    var imported by remember { mutableStateOf<KThemeSpec?>(null) }
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var text by remember { mutableStateOf(SampleThemeBuilder) }
    var error by remember { mutableStateOf<String?>(null) }
    val spec = imported ?: preset
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KompoundTheme(spec, dark, strings = language.second, density = density) {
            KSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KText("${imported?.name ?: preset.name}, ${density.name.lowercase()}, ${language.first}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KButton({}) { KText("Filled") }
                        KButton({}, variant = KButtonVariant.Tonal) { KText("Tonal") }
                        KButton({}, variant = KButtonVariant.Outlined) { KText("Outlined") }
                        KChip("Chip", {})
                        KBadge("3", tone = KBadgeTone.Primary)
                        KAvatar("Ada Lovelace", status = KAvatarStatus.Away)
                    }
                    KTextField("", {}, Modifier.fillMaxWidth(), label = "Name", placeholder = "Ada")
                    KSearchBar("", {}, Modifier.fillMaxWidth())
                    KListItem("List item", Modifier.fillMaxWidth(), supporting = "Supporting text")
                    KStepList(listOf(KStep("Download", KStepState.Done), KStep("Install", KStepState.InProgress(0.4f)), KStep("Restart", KStepState.Waiting)))
                }
            }
        }
        KText("Import a theme (Material Theme Builder JSON, or design tokens):")
        KTextArea(text, { text = it; error = null }, Modifier.fillMaxWidth(), label = "JSON", minLines = 4, maxLines = 8, isError = error != null, supportingText = error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fun run(read: () -> tech.kloos.kompound.theme.KThemeImportResult) {
                try {
                    val result = read()
                    imported = result.theme
                    warnings = result.warnings
                    error = null
                } catch (e: KJsonException) {
                    error = e.message
                }
            }
            KButton({ run { KThemeImport.fromMaterialThemeBuilder(text, "Imported") } }) { KText("Import Material Theme Builder") }
            KButton({ run { KThemeImport.fromDesignTokens(text, "Imported") } }, variant = KButtonVariant.Tonal) { KText("Import design tokens") }
            KButton({ imported = null; warnings = emptyList() }, variant = KButtonVariant.Text, enabled = imported != null) { KText("Use the preset") }
        }
        warnings.take(6).forEach { KText("Note: $it") }
    }
}
