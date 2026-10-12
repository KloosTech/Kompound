package tech.kloos.kompound.catalog.macos

import androidx.compose.ui.window.Window
import platform.AppKit.NSApp
import platform.AppKit.NSApplication
import tech.kloos.kompound.catalog.ui.KompoundCatalog

fun main() {
    NSApplication.sharedApplication()
    Window("Kompound Catalog (macOS native)") {
        KompoundCatalog()
    }
    NSApp?.run()
}
