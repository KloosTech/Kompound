package tech.kloos.kompound.catalog.harness

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.catalog.theme.hsl
import kotlin.random.Random

/**
 * A random theme for style fuzzing: every colour role of the Material 3 scheme gets its own random colour and every corner radius a random size,
 * with no regard for contrast or taste. Components must still lay out, stay inside their bounds and not crash; what they cannot do is look good,
 * so contrast checks do not apply. The same seed gives the same theme on every platform ([Random] with a seed is specified).
 */
object ChaosTheme {
    private fun colour(r: Random, light: Boolean): Color =
        hsl(r.nextFloat() * 360f, r.nextFloat(), if (light) 0.15f + r.nextFloat() * 0.8f else r.nextFloat())

    fun colorScheme(seed: Long, dark: Boolean): ColorScheme {
        val r = Random(seed * 2 + if (dark) 1 else 0)
        fun c() = colour(r, !dark)
        return if (dark) darkColorScheme(
            primary = c(), onPrimary = c(), primaryContainer = c(), onPrimaryContainer = c(), inversePrimary = c(),
            secondary = c(), onSecondary = c(), secondaryContainer = c(), onSecondaryContainer = c(),
            tertiary = c(), onTertiary = c(), tertiaryContainer = c(), onTertiaryContainer = c(),
            background = c(), onBackground = c(), surface = c(), onSurface = c(), surfaceVariant = c(), onSurfaceVariant = c(), surfaceTint = c(),
            inverseSurface = c(), inverseOnSurface = c(), error = c(), onError = c(), errorContainer = c(), onErrorContainer = c(),
            outline = c(), outlineVariant = c(), scrim = c(),
            surfaceBright = c(), surfaceDim = c(), surfaceContainer = c(), surfaceContainerHigh = c(), surfaceContainerHighest = c(),
            surfaceContainerLow = c(), surfaceContainerLowest = c(),
        ) else lightColorScheme(
            primary = c(), onPrimary = c(), primaryContainer = c(), onPrimaryContainer = c(), inversePrimary = c(),
            secondary = c(), onSecondary = c(), secondaryContainer = c(), onSecondaryContainer = c(),
            tertiary = c(), onTertiary = c(), tertiaryContainer = c(), onTertiaryContainer = c(),
            background = c(), onBackground = c(), surface = c(), onSurface = c(), surfaceVariant = c(), onSurfaceVariant = c(), surfaceTint = c(),
            inverseSurface = c(), inverseOnSurface = c(), error = c(), onError = c(), errorContainer = c(), onErrorContainer = c(),
            outline = c(), outlineVariant = c(), scrim = c(),
            surfaceBright = c(), surfaceDim = c(), surfaceContainer = c(), surfaceContainerHigh = c(), surfaceContainerHighest = c(),
            surfaceContainerLow = c(), surfaceContainerLowest = c(),
        )
    }

    /** Corner radii from 0 to 40 dp, independent per size. */
    fun shapes(seed: Long): Shapes {
        val r = Random(seed * 3 + 5)
        fun s() = RoundedCornerShape((r.nextFloat() * 40f).dp)
        return Shapes(extraSmall = s(), small = s(), medium = s(), large = s(), extraLarge = s())
    }
}
