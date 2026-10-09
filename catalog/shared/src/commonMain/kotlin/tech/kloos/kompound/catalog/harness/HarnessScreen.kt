package tech.kloos.kompound.catalog.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import tech.kloos.kompound.catalog.theme.ThemeMode
import tech.kloos.kompound.catalog.theme.ThemeSettings
import tech.kloos.kompound.catalog.ui.ControlPanel
import tech.kloos.kompound.demo.DemoControls
import tech.kloos.kompound.demo.DemoEntry
import tech.kloos.kompound.i18n.KompoundStrings
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KDensity
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Runs [content] in the environment a [HarnessLaunch] asks for: theme, absolute font scale, layout direction, control density and the language of
 * Kompound's own labels. Nothing here depends on the device's own settings, so a flow gets the same pixels on any phone.
 */
@Composable
fun HarnessEnvironment(launch: HarnessLaunch, content: @Composable () -> Unit) {
    val settings = ThemeSettings(hue = launch.hue, saturation = launch.saturation, roundness = launch.roundness, mode = if (launch.dark) ThemeMode.Dark else ThemeMode.Light)
    val base = LocalDensity.current
    val density = when (launch.density) { "compact" -> KDensity.Compact; "spacious" -> KDensity.Spacious; else -> KDensity.Comfortable }
    CompositionLocalProvider(
        LocalLayoutDirection provides if (launch.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides Density(base.density, launch.fontScale),
    ) {
        KompoundTheme(
            colorScheme = launch.chaos?.let { ChaosTheme.colorScheme(it, launch.dark) } ?: settings.colorScheme(launch.dark),
            shapes = launch.chaos?.let { ChaosTheme.shapes(it) } ?: settings.shapes(),
            strings = KompoundStrings.forLanguageTag(launch.lang), density = density,
            content = content,
        )
    }
}

/**
 * The bare harness view of one demo: its preview and its controls and nothing else, on a plain background. A UI test flow addresses it by
 * test tags (ADR 0008): `harness:ready` appears (described by the demo's qualified id) once the demo composed and two frames passed, so a flow waits
 * for it instead of for time; `harness:preview` wraps the demo; every control carries `control:<name>` (`control:<name>:value` for a slider's readout,
 * `control:<name>=<option>` for a choice's chips).
 */
@Composable
fun HarnessScreen(entry: DemoEntry, launch: HarnessLaunch, modifier: Modifier = Modifier) {
    // A new link while the app runs (flows open one demo after another) must rebuild everything, controls included: key on the whole launch.
    androidx.compose.runtime.key(entry.qualifiedId, launch) {
    val controls = remember { DemoControls(launch.controls) }
    HarnessEnvironment(launch) {
        Column(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HarnessReady(entry.qualifiedId)
            val content = entry.content
            Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("harness:preview"), verticalArrangement = Arrangement.Center) { controls.content() }
            if (controls.controls.isNotEmpty()) {
                Column(Modifier.testTag("harness:controls"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KText("Controls")
                    ControlPanel(controls.controls)
                }
            }
        }
    }
    }
}

/** A 1 dp node that exists once the demo is on screen and settled; its content description is the demo id. */
@Composable
private fun HarnessReady(id: String) {
    var ready by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        withFrameNanos { }
        withFrameNanos { }
        ready = true
    }
    if (ready) Box(Modifier.size(1.dp).testTag("harness:ready").semantics { contentDescription = id })
}
