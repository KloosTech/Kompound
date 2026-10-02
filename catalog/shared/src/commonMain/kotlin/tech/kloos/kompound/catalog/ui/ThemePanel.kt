package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.catalog.theme.ThemeMode
import tech.kloos.kompound.catalog.theme.ThemePreset
import tech.kloos.kompound.catalog.theme.ThemeSettings
import tech.kloos.kompound.catalog.theme.hsl
import tech.kloos.kompound.catalog.theme.toKotlin
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.segmented.KSegmentedControl
import tech.kloos.kompound.slider.KSlider
import tech.kloos.kompound.surface.KSurface
import tech.kloos.kompound.text.KText

private val HueGradient: Brush = Brush.horizontalGradient(List(7) { hsl(it * 60f, 0.8f, 0.55f) })

/** The theme designer: everything a visitor can change, applied live to the whole catalog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemePanel(
    settings: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
    onReset: () -> Unit,
    onGetCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = settings.colorScheme(settings.isDark(androidx.compose.foundation.isSystemInDarkTheme())).primary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Section("Appearance") {
            KSegmentedControl(
                options = ThemeMode.entries.map { it.name }, selectedIndex = settings.mode.ordinal,
                onSelectedIndexChange = { onChange(settings.copy(mode = ThemeMode.entries[it])) },
            )
        }
        Section("Colour") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeSettings.Presets.forEach { preset ->
                    Swatch(preset, selected = preset.hue == settings.hue && preset.saturation == settings.saturation) {
                        onChange(settings.copy(hue = preset.hue, saturation = preset.saturation))
                    }
                }
            }
            LabeledSlider("Hue", "${settings.hue.toInt()}°", settings.hue, 0f..360f, { onChange(settings.copy(hue = it)) }, trackStyle = Style { background(HueGradient) }, activeTrackStyle = Style { background(Color.Transparent) })
            LabeledSlider("Saturation", "${(settings.saturation * 100).toInt()}%", settings.saturation, 0.05f..1f, { onChange(settings.copy(saturation = it)) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(primary))
                KText("Primary ${primary.toKotlin().removePrefix("Color(0xFF").removeSuffix(")")}", style = textRole(quiet = true) { it.labelMedium })
            }
        }
        Section("Shape and type") {
            LabeledSlider("Roundness", "${(settings.roundness * 100).toInt()}%", settings.roundness, 0f..2f, { onChange(settings.copy(roundness = it)) })
            LabeledSlider("Text size", "${(settings.textScale * 100).toInt()}%", settings.textScale, 0.8f..1.4f, { onChange(settings.copy(textScale = it)) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            KButton(onClick = onReset, variant = KButtonVariant.Text) { KIcon(CatalogIcons.Reset, null); KText("Reset", Modifier.padding(start = 8.dp)) }
            KButton(onClick = onGetCode) { KIcon(CatalogIcons.Code, null); KText("Get code", Modifier.padding(start = 8.dp)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KText(title.uppercase(), style = textRole(quiet = true, weight = androidx.compose.ui.text.font.FontWeight.SemiBold) { it.labelSmall })
        content()
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit, trackStyle: Style = Style, activeTrackStyle: Style = Style,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KText(label, Modifier.weight(1f), style = textRole { it.labelLarge })
            KText(value, style = textRole(quiet = true) { it.labelMedium })
        }
        KSlider(current, onChange, valueRange = range, trackStyle = trackStyle, activeTrackStyle = activeTrackStyle)
    }
}

@Composable
private fun Swatch(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val colour = hsl(preset.hue, preset.saturation.coerceAtLeast(0.15f), 0.5f)
    val ring = MaterialTheme.colorScheme.onSurface
    val style = remember(colour, ring, selected) {
        Style { background(colour); shape(CircleShape); size(36.dp); if (selected) { borderWidth(3.dp); borderColor(ring) } }
    }
    KSurface(onClick = onClick, Modifier.semantics { contentDescription = preset.name }, style = style) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { if (selected) KIcon(CatalogIcons.Check, null, tint = Color.White) }
    }
}
