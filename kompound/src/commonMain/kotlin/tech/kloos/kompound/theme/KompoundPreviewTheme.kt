package tech.kloos.kompound.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Wrapper for `@Preview`s and screenshot tests: the theme, a page background and the switches a visual check usually needs in one place.
 * Not meant for production screens (use [KompoundTheme] or [KPlatformTheme] there).
 *
 * ```
 * @Preview @Composable fun ChatPreview() = KompoundPreviewTheme(dark = true) { ChatContent(sample) }
 * ```
 *
 * @param spec Colour theme.
 * @param dark Dark or light scheme (not the system's, so a preview is reproducible).
 * @param density Control sizing.
 * @param rtl Lay the content out right to left.
 * @param look Type, shapes and density of the platform; its density wins over [density] when [look] is not the default.
 * @param padding Space around the content.
 */
@Composable
public fun KompoundPreviewTheme(
    spec: KThemeSpec = KThemePresets.Default,
    dark: Boolean = false,
    density: KDensity = KDensity.Comfortable,
    rtl: Boolean = false,
    look: KPlatformLook = KPlatformLook.Material,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        KPlatformTheme(spec, if (look === KPlatformLook.Material) KPlatformLook(null, null, density) else look, dark) {
            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(padding)) { content() }
        }
    }
}
