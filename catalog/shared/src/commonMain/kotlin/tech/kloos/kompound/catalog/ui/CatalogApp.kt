package tech.kloos.kompound.catalog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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
fun KompoundCatalog(entries: List<DemoEntry> = KompoundAllDemos.entries, initialSettings: ThemeSettings = ThemeSettings()) {
    var settings by remember { mutableStateOf(initialSettings) }
    val state = remember(entries) { CatalogState(entries) }
    val dark = settings.isDark(isSystemInDarkTheme())
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * settings.textScale)) {
        KompoundTheme(colorScheme = settings.colorScheme(dark), shapes = settings.shapes()) {
            CatalogShell(state, settings, dark) { settings = it }
        }
    }
}
