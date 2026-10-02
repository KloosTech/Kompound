package tech.kloos.kompound.buttons

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap

/** Distinct hues so every role can be told apart in a screenshot. */
internal val ButtonTestScheme = lightColorScheme(
    primary = Color(0xFF0000FF),
    onPrimary = Color(0xFFFFFF00),
    primaryContainer = Color(0xFFFF8000),
    onPrimaryContainer = Color(0xFF00FFFF),
    secondaryContainer = Color(0xFF00FF00),
    onSecondaryContainer = Color(0xFFFF00FF),
    outline = Color(0xFFFF0000),
    onSurface = Color(0xFF000000),
)

/** Pixel on the top edge, horizontally centred, `inset` px down: inside shapes with rounded corners. */
internal fun ImageBitmap.topCentre(inset: Int = 3): Color = toPixelMap()[width / 2, inset]
