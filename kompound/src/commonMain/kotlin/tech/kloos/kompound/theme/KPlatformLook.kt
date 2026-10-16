package tech.kloos.kompound.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.intl.Locale
import tech.kloos.kompound.i18n.KompoundStrings
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * The non-colour half of a theme: type scale, corner radii and [density]. Colours come from a [KThemeSpec]; combine both
 * with [KPlatformTheme]. Kompound cannot tell the platform in common code, so pick the look with your own check:
 * `KPlatformTheme(spec, look = if (isMac) KPlatformLook.Mac else KPlatformLook.Material) { ... }`.
 *
 * @property typography Type scale; `null` keeps the one of the surrounding `MaterialTheme`.
 * @property shapes Corner radii; `null` keeps the surrounding ones.
 * @property density Control sizing.
 */
@Immutable
public class KPlatformLook(
    public val typography: Typography?,
    public val shapes: Shapes?,
    public val density: KDensity,
) {
    public companion object {
        /** Material 3's own sizing, type and shapes. */
        public val Material: KPlatformLook = KPlatformLook(null, null, KDensity.Comfortable)

        /**
         * Desktop macOS look: the system font, the compact type scale of Apple's Human Interface Guidelines (13sp body text),
         * tighter corner radii and [KDensity.Compact] controls.
         */
        public val Mac: KPlatformLook = KPlatformLook(macTypography(), macShapes(), KDensity.Compact)
    }
}

private fun macTypography(): Typography {
    val base = Typography()
    fun TextStyle.mac(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
    return Typography(
        displayLarge = base.displayLarge.mac(34, 40),
        displayMedium = base.displayMedium.mac(28, 34),
        displaySmall = base.displaySmall.mac(24, 30),
        headlineLarge = base.headlineLarge.mac(26, 32),
        headlineMedium = base.headlineMedium.mac(22, 28),
        headlineSmall = base.headlineSmall.mac(17, 22),
        titleLarge = base.titleLarge.mac(17, 22, FontWeight.Medium),
        titleMedium = base.titleMedium.mac(15, 20, FontWeight.Medium),
        titleSmall = base.titleSmall.mac(13, 18, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.mac(14, 20),
        bodyMedium = base.bodyMedium.mac(13, 18),
        bodySmall = base.bodySmall.mac(11, 16),
        labelLarge = base.labelLarge.mac(13, 18, FontWeight.Medium),
        labelMedium = base.labelMedium.mac(12, 16, FontWeight.Medium),
        labelSmall = base.labelSmall.mac(11, 14, FontWeight.Medium),
    )
}

private fun macShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

/**
 * [KompoundTheme] from a colour [spec] and a [look] (type, shapes, density) in one call.
 *
 * @param spec The colour theme.
 * @param look The platform look; [KPlatformLook.Material] by default.
 * @param dark Use the dark scheme; follows the system by default.
 */
@Composable
public fun KPlatformTheme(
    spec: KThemeSpec,
    look: KPlatformLook = KPlatformLook.Material,
    dark: Boolean = isSystemInDarkTheme(),
    strings: KompoundStrings = KompoundStrings.forLocale(Locale.current),
    content: @Composable () -> Unit,
) {
    KompoundTheme(
        spec, dark,
        typography = look.typography ?: MaterialTheme.typography,
        shapes = look.shapes ?: MaterialTheme.shapes,
        strings = strings,
        density = look.density,
        content = content,
    )
}
