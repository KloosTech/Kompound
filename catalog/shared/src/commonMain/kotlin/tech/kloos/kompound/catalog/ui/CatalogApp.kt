package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.catalog.theme.ThemeMode
import tech.kloos.kompound.catalog.harness.HarnessScreen
import tech.kloos.kompound.catalog.harness.HarnessLaunch
import tech.kloos.kompound.catalog.harness.HarnessEnvironment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import tech.kloos.kompound.catalog.KompoundAllDemos
import tech.kloos.kompound.catalog.theme.ThemeSettings
import tech.kloos.kompound.demo.DemoEntry
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Entry point for every launcher (android, ios, desktop, web). The catalog is built from Kompound's own
 * components and has a live theme designer: the whole page, demos included, re-themes as the visitor edits.
 */
@Composable
fun KompoundCatalog(entries: List<DemoEntry> = KompoundAllDemos.entries, initialSettings: ThemeSettings = ThemeSettings(), launch: HarnessLaunch? = null) {
    // A UI test harness (ADR 0008) opens one demo directly: bare (only the demo and its controls) or inside the normal catalog.
    val target = launch?.resolve(entries)
    if (launch != null && launch.bare) {
        if (target != null) HarnessScreen(target, launch)
        else HarnessEnvironment(launch) { KText("Unknown demo: ${launch.demo}", Modifier.testTag("harness:error")) }
        return
    }
    var settings by remember { mutableStateOf(if (launch != null) initialSettings.copy(mode = if (launch.dark) ThemeMode.Dark else ThemeMode.Light) else initialSettings) }
    val state = remember(entries) { CatalogState(entries).also { if (target != null) it.selectedId = target.qualifiedId } }
    val dark = settings.isDark(isSystemInDarkTheme())
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * settings.textScale)) {
        KompoundTheme(colorScheme = settings.colorScheme(dark), shapes = settings.shapes()) {
            CatalogShell(state, settings, dark) { settings = it }
        }
    }
}
