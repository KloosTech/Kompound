package tech.kloos.kompound.catalog.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tech.kloos.kompound.catalog.ui.KompoundCatalog

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kompound Catalog") {
        KompoundCatalog()
    }
}
