package tech.kloos.kompound.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic colours M3 does not define. Everything else comes from the M3 `ColorScheme`, so apps that
 * already use M3 theming need no second colour system.
 */
@Immutable
public class KompoundColors(
    public val success: Color,
    public val onSuccess: Color,
    public val successContainer: Color,
    public val onSuccessContainer: Color,
    public val warning: Color,
    public val onWarning: Color,
    public val warningContainer: Color,
    public val onWarningContainer: Color,
    public val info: Color,
    public val onInfo: Color,
    public val infoContainer: Color,
    public val onInfoContainer: Color,
) {
    public fun copy(
        success: Color = this.success,
        onSuccess: Color = this.onSuccess,
        successContainer: Color = this.successContainer,
        onSuccessContainer: Color = this.onSuccessContainer,
        warning: Color = this.warning,
        onWarning: Color = this.onWarning,
        warningContainer: Color = this.warningContainer,
        onWarningContainer: Color = this.onWarningContainer,
        info: Color = this.info,
        onInfo: Color = this.onInfo,
        infoContainer: Color = this.infoContainer,
        onInfoContainer: Color = this.onInfoContainer,
    ): KompoundColors = KompoundColors(
        success, onSuccess, successContainer, onSuccessContainer,
        warning, onWarning, warningContainer, onWarningContainer,
        info, onInfo, infoContainer, onInfoContainer,
    )

    public companion object {
        public val Light: KompoundColors = KompoundColors(
            success = Color(0xFF1B7F3B), onSuccess = Color(0xFFFFFFFF),
            successContainer = Color(0xFFD3F4DC), onSuccessContainer = Color(0xFF00391B),
            warning = Color(0xFF8A5100), onWarning = Color(0xFFFFFFFF),
            warningContainer = Color(0xFFFFDDB3), onWarningContainer = Color(0xFF2D1600),
            info = Color(0xFF00629B), onInfo = Color(0xFFFFFFFF),
            infoContainer = Color(0xFFCDE5FF), onInfoContainer = Color(0xFF001D32),
        )
        public val Dark: KompoundColors = KompoundColors(
            success = Color(0xFF7CDA94), onSuccess = Color(0xFF00391B),
            successContainer = Color(0xFF00522A), onSuccessContainer = Color(0xFFD3F4DC),
            warning = Color(0xFFFFB86B), onWarning = Color(0xFF4A2800),
            warningContainer = Color(0xFF6A3C00), onWarningContainer = Color(0xFFFFDDB3),
            info = Color(0xFF98CBFF), onInfo = Color(0xFF003353),
            infoContainer = Color(0xFF004A77), onInfoContainer = Color(0xFFCDE5FF),
        )
    }
}

/** Spacing scale. Use these instead of literal `dp` values in components (C-010). */
@Immutable
public class KompoundSpacing(
    public val xxs: Dp = 2.dp,
    public val xs: Dp = 4.dp,
    public val sm: Dp = 8.dp,
    public val md: Dp = 12.dp,
    public val lg: Dp = 16.dp,
    public val xl: Dp = 24.dp,
    public val xxl: Dp = 32.dp,
    public val xxxl: Dp = 48.dp,
)

/** Durations in milliseconds and easings shared by all component animations. */
@Immutable
public class KompoundMotion(
    public val durationShort: Int = 100,
    public val durationMedium: Int = 200,
    public val durationLong: Int = 300,
    public val standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
    public val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)

/** Opacities for interaction state layers and disabled content (values follow Material 3). */
@Immutable
public class KompoundStateLayer(
    public val hovered: Float = 0.08f,
    public val focused: Float = 0.10f,
    public val pressed: Float = 0.10f,
    public val dragged: Float = 0.16f,
    public val disabledContent: Float = 0.38f,
    public val disabledContainer: Float = 0.12f,
)

/** Everything Kompound adds on top of the M3 theme. Read it with [KompoundTheme.tokens]. */
@Immutable
public class KompoundTokens(
    public val colors: KompoundColors = KompoundColors.Light,
    public val spacing: KompoundSpacing = KompoundSpacing(),
    public val motion: KompoundMotion = KompoundMotion(),
    public val stateLayer: KompoundStateLayer = KompoundStateLayer(),
) {
    public fun copy(
        colors: KompoundColors = this.colors,
        spacing: KompoundSpacing = this.spacing,
        motion: KompoundMotion = this.motion,
        stateLayer: KompoundStateLayer = this.stateLayer,
    ): KompoundTokens = KompoundTokens(colors, spacing, motion, stateLayer)

    public companion object {
        public val Light: KompoundTokens = KompoundTokens(colors = KompoundColors.Light)
        public val Dark: KompoundTokens = KompoundTokens(colors = KompoundColors.Dark)

        /** Picks the light or dark extension colours to match [colorScheme]'s background. */
        public fun forColorScheme(colorScheme: ColorScheme): KompoundTokens =
            if (colorScheme.background.luminance() > 0.5f) Light else Dark
    }
}
