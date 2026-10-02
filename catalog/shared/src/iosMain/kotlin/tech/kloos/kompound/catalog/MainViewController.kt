package tech.kloos.kompound.catalog

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import tech.kloos.kompound.catalog.ui.KompoundCatalog

/** Called from SwiftUI (`ContentView.swift`). */
fun MainViewController(): UIViewController = ComposeUIViewController { KompoundCatalog() }
