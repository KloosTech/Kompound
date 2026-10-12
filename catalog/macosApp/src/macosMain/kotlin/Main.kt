package tech.kloos.kompound.catalog.macos

import androidx.compose.ui.window.Window
import platform.AppKit.NSApp
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import tech.kloos.kompound.catalog.ui.KompoundCatalog

fun main() {
    NSApplication.sharedApplication()
    // A bare executable is not a regular app by default: without this its window never becomes key and gets no keyboard input
    NSApp?.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
    Window("Kompound Catalog (macOS native)") {
        KompoundCatalog()
    }
    NSApp?.activateIgnoringOtherApps(true)
    NSApp?.run()
}
