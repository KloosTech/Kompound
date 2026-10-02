package tech.kloos.kompound.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KompoundThemeTest {
    private fun <T> read(content: @Composable (capture: (T) -> Unit) -> Unit): T? {
        var out: T? = null
        runComposeUiTest { setContent { content { out = it } } }
        return out
    }

    @Test
    fun tokensDefaultToLightWithoutTheme() {
        val t = read<KompoundTokens> { capture -> capture(KompoundTheme.tokens) }
        assertSame(KompoundTokens.Light, t)
    }

    @Test
    fun lightSchemeSelectsLightTokensAndDarkSelectsDark() {
        val light = read<KompoundTokens> { c -> KompoundTheme(colorScheme = lightColorScheme()) { c(KompoundTheme.tokens) } }
        val dark = read<KompoundTokens> { c -> KompoundTheme(colorScheme = darkColorScheme()) { c(KompoundTheme.tokens) } }
        assertSame(KompoundTokens.Light, light)
        assertSame(KompoundTokens.Dark, dark)
    }

    @Test
    fun customTokensAreProvided() {
        val custom = KompoundTokens.Light.copy(colors = KompoundColors.Light.copy(success = Color.Magenta))
        val t = read<KompoundTokens> { c -> KompoundTheme(tokens = custom) { c(KompoundTheme.tokens) } }
        assertEquals(Color.Magenta, t!!.colors.success)
    }

    @Test
    fun m3ThemeIsProvidedToContent() {
        val scheme = lightColorScheme(primary = Color(0xFF123456))
        val primary = read<Color> { c -> KompoundTheme(colorScheme = scheme) { c(MaterialTheme.colorScheme.primary) } }
        assertEquals(Color(0xFF123456), primary)
    }

    @Test
    fun extensionColourPairsMeetWcagAaForText() {
        for ((name, colors) in listOf("light" to KompoundColors.Light, "dark" to KompoundColors.Dark)) {
            val pairs = listOf(
                "success" to (colors.success to colors.onSuccess),
                "successContainer" to (colors.successContainer to colors.onSuccessContainer),
                "warning" to (colors.warning to colors.onWarning),
                "warningContainer" to (colors.warningContainer to colors.onWarningContainer),
                "info" to (colors.info to colors.onInfo),
                "infoContainer" to (colors.infoContainer to colors.onInfoContainer),
            )
            for ((pairName, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5f, "$name $pairName contrast $ratio < 4.5")
            }
        }
    }
}
