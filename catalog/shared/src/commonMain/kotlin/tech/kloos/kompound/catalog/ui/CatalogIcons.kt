package tech.kloos.kompound.catalog.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons the catalog app draws (Material Symbols path data, Apache-2.0, Google). Components themselves take icons as slots. */
internal object CatalogIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

    val Menu by lazy { icon("menu", "M3,18h18v-2L3,16v2zM3,13h18v-2L3,11v2zM3,6v2h18L21,6L3,6z") }
    val Category by lazy {
        icon("category", "M3,3v8h8L11,3L3,3zM9,9L5,9L5,5h4v4zM3,13v8h8v-8L3,13zM9,19L5,19v-4h4v4zM13,3v8h8L21,3h-8zM19,9h-4L15,5h4v4zM13,13v8h8v-8h-8zM19,19h-4v-4h4v4z")
    }
    val Tag by lazy {
        icon("tag", "M21.41,11.58l-9,-9C12.05,2.22 11.55,2 11,2H4C2.9,2 2,2.9 2,4v7c0,0.55 0.22,1.05 0.59,1.42l9,9C11.95,21.78 12.45,22 13,22c0.55,0 1.05,-0.22 1.41,-0.59l7,-7C21.78,14.05 22,13.55 22,13C22,12.45 21.77,11.94 21.41,11.58zM5.5,7C4.67,7 4,6.33 4,5.5S4.67,4 5.5,4S7,4.67 7,5.5S6.33,7 5.5,7z")
    }
    val Palette by lazy {
        icon("palette", "M12,2C6.49,2 2,6.49 2,12s4.49,10 10,10c1.38,0 2.5,-1.12 2.5,-2.5 0,-0.61 -0.23,-1.2 -0.64,-1.67 -0.08,-0.1 -0.13,-0.21 -0.13,-0.33 0,-0.28 0.22,-0.5 0.5,-0.5L16,17c3.31,0 6,-2.69 6,-6 0,-4.96 -4.49,-9 -10,-9zM6.5,13C5.67,13 5,12.33 5,11.5S5.67,10 6.5,10 8,10.67 8,11.5 7.33,13 6.5,13zM9.5,9C8.67,9 8,8.33 8,7.5S8.67,6 9.5,6 11,6.67 11,7.5 10.33,9 9.5,9zM14.5,9C13.67,9 13,8.33 13,7.5S13.67,6 14.5,6 16,6.67 16,7.5 15.33,9 14.5,9zM17.5,13c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z")
    }
    val DarkMode by lazy {
        icon("dark_mode", "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4 -0.44,-0.06 -0.9,-0.1 -1.36,-0.1z")
    }
    val LightMode by lazy {
        icon("light_mode", "M12,7a5,5 0 1,0 0,10a5,5 0 1,0 0,-10zM11,1h2v3h-2zM11,20h2v3h-2zM1,11h3v2H1zM20,11h3v2h-3zM4.2,5.6l1.4,-1.4 2.1,2.1 -1.4,1.4zM16.3,17.7l1.4,-1.4 2.1,2.1 -1.4,1.4zM17.7,4.2l1.4,1.4 -2.1,2.1 -1.4,-1.4zM6.3,16.3l1.4,1.4 -2.1,2.1 -1.4,-1.4z")
    }
    val OpenInNew by lazy {
        icon("open_in_new", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
    }
    val ArrowBack by lazy { icon("arrow_back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z") }
    val Copy by lazy {
        icon("content_copy", "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z")
    }
    val Code by lazy {
        icon("code", "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z")
    }
    val Close by lazy {
        icon("close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    }
    val Check by lazy { icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val Reset by lazy {
        icon("refresh", "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z")
    }

    val all: List<ImageVector> get() = listOf(Menu, Category, Tag, Palette, DarkMode, LightMode, OpenInNew, ArrowBack, Copy, Code, Close, Check, Reset)
}
