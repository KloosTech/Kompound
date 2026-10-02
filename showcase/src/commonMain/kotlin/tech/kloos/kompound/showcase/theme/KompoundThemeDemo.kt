package tech.kloos.kompound.showcase.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.surface.KSurface
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

private const val Usage_theme_tokens = """import tech.kloos.kompound.theme.KompoundTheme

// Wrap your app once. Colours, shapes and type come from the Material 3 ColorScheme you pass.
KompoundTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C4DFF))) {
    App()
}

// Inside the theme, read Kompound's extra tokens.
val hover = KompoundTheme.tokens.stateLayer.hovered
val spacing = KompoundTheme.tokens.spacing"""

@KompoundDemo(
    id = "theme.tokens",
    title = "KompoundTheme tokens",
    description = "Semantic colours, spacing scale and state layers added on top of the Material 3 theme.",
    category = KompoundCategory.Foundations,
    tags = ["theme", "tokens", "color", "spacing", "dark mode"],
    since = "0.1.0",
    usage = Usage_theme_tokens,
)
@Composable
fun DemoScope.KompoundThemeDemo() {
    val dark = boolControl("Dark theme", false)
    KompoundTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        val colors = KompoundTheme.tokens.colors
        val spacing = KompoundTheme.tokens.spacing
        KSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Swatch("Success", colors.success, colors.onSuccess, colors.successContainer, colors.onSuccessContainer)
                Swatch("Warning", colors.warning, colors.onWarning, colors.warningContainer, colors.onWarningContainer)
                Swatch("Info", colors.info, colors.onInfo, colors.infoContainer, colors.onInfoContainer)
                KText("Spacing scale")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                    listOf(spacing.xxs, spacing.xs, spacing.sm, spacing.md, spacing.lg, spacing.xl, spacing.xxl).forEach {
                        Box(Modifier.size(it).background(colors.info))
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(name: String, main: Color, onMain: Color, container: Color, onContainer: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(40.dp).background(main), contentAlignment = Alignment.Center) { KText(name, style = androidx.compose.foundation.style.Style { contentColor(onMain) }) }
        Box(Modifier.weight(1f).height(40.dp).background(container), contentAlignment = Alignment.Center) { KText("$name container", style = androidx.compose.foundation.style.Style { contentColor(onContainer) }) }
    }
}
